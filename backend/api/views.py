from django.db import connection
from rest_framework import generics
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView
from rest_framework_simplejwt.exceptions import TokenError
from rest_framework_simplejwt.tokens import RefreshToken
from rest_framework_simplejwt.views import TokenObtainPairView

from .models import DriverProfile, PanicAlert, RideHistory
from .permissions import IsOperator
from .serializers import (
    CopilotoTokenObtainPairSerializer,
    DriverProfileSerializer,
    PanicAlertCreateSerializer,
    PanicAlertSerializer,
    PanicAlertUpdateSerializer,
    RegisterSerializer,
    RideHistorySerializer,
    add_claims,
)


def tokens_for(user) -> dict:
    """Builds an access/refresh pair for a user (Module 3 — JWT auth)."""
    refresh = add_claims(RefreshToken.for_user(user), user)
    return {"access": str(refresh.access_token), "refresh": str(refresh)}


class HealthView(APIView):
    """Liveness/readiness probe for monitoring and load balancers."""

    permission_classes = [AllowAny]
    authentication_classes = []
    throttle_classes = []

    def get(self, request):
        try:
            connection.ensure_connection()
            db_ok = True
        except Exception:
            db_ok = False
        return Response({"status": "ok" if db_ok else "degraded", "db": db_ok},
                        status=200 if db_ok else 503)


class LoginView(TokenObtainPairView):
    """JWT login, rate-limited to slow down credential stuffing."""

    throttle_scope = "auth"
    serializer_class = CopilotoTokenObtainPairSerializer


class RegisterView(APIView):
    """Creates a driver account and returns a JWT pair."""

    permission_classes = [AllowAny]
    throttle_scope = "auth"

    def post(self, request):
        serializer = RegisterSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = serializer.save()
        return Response(
            {"user": {"username": user.username, "email": user.email}, **tokens_for(user)},
            status=201,
        )


class LogoutView(APIView):
    """Blacklists the refresh token so logout actually invalidates the session."""

    permission_classes = [IsAuthenticated]

    def post(self, request):
        token = request.data.get("refresh")
        if not token:
            return Response({"detail": "refresh token obrigatório"}, status=400)
        try:
            RefreshToken(token).blacklist()
        except TokenError:
            # Already expired/blacklisted — the client is logged out either way.
            pass
        return Response(status=205)


class MeView(APIView):
    """Identifies the current user and whether they are a Central operator."""

    permission_classes = [IsAuthenticated]

    def get(self, request):
        user = request.user
        return Response({
            "username": user.username,
            "email": user.email,
            "is_operator": bool(user.is_staff),
        })


class RideListCreateView(generics.ListCreateAPIView):
    """GET lists the driver's rides; POST backs up a captured ride (Module F)."""

    serializer_class = RideHistorySerializer

    def get_queryset(self):
        return RideHistory.objects.filter(driver__user=self.request.user)

    def perform_create(self, serializer):
        driver, _ = DriverProfile.objects.get_or_create(user=self.request.user)
        serializer.save(driver=driver)


class DriverProfileView(APIView):
    """GET returns (creating defaults if needed) and PUT updates the config."""

    def get(self, request):
        profile, _ = DriverProfile.objects.get_or_create(user=request.user)
        return Response(DriverProfileSerializer(profile).data)

    def put(self, request):
        profile, _ = DriverProfile.objects.get_or_create(user=request.user)
        serializer = DriverProfileSerializer(profile, data=request.data)
        serializer.is_valid(raise_exception=True)
        serializer.save()
        return Response(serializer.data)


class PanicAlertListCreateView(APIView):
    """GET lists alerts for the Central de Operações; POST fires an alert (driver)."""

    def get_permissions(self):
        # A driver fires an alert (any authenticated user); only Central operators
        # may list alerts, since the list exposes location + transcript of people
        # possibly in danger.
        if self.request.method == "POST":
            return [IsAuthenticated()]
        return [IsOperator()]

    def get(self, request):
        alerts = PanicAlert.objects.select_related("driver__user").all()
        return Response(PanicAlertSerializer(alerts, many=True).data)

    def post(self, request):
        serializer = PanicAlertCreateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        driver, _ = DriverProfile.objects.get_or_create(user=request.user)
        alert = PanicAlert.objects.create(driver=driver, **serializer.validated_data)
        return Response(PanicAlertSerializer(alert).data, status=201)


class PanicAlertDetailView(APIView):
    """Operator action on an alert (Central de Operações)."""

    permission_classes = [IsOperator]

    def patch(self, request, pk):
        try:
            alert = PanicAlert.objects.select_related("driver__user").get(pk=pk)
        except PanicAlert.DoesNotExist:
            return Response({"detail": "alerta não encontrado"}, status=404)

        serializer = PanicAlertUpdateSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        action = serializer.validated_data["operator_action"]
        alert.operator_action = action

        new_status = serializer.validated_data.get("status")
        if new_status:
            alert.status = new_status
        elif action == PanicAlert.OperatorAction.FALSO_POSITIVO:
            alert.status = PanicAlert.Status.ENCERRADO
        else:
            alert.status = PanicAlert.Status.EM_ATENDIMENTO
        alert.save()
        return Response(PanicAlertSerializer(alert).data)
