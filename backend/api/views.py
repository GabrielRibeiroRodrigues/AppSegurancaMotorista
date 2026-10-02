from rest_framework import generics
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView
from rest_framework_simplejwt.tokens import RefreshToken

from .models import DriverProfile, PanicAlert, RideHistory
from .serializers import (
    DriverProfileSerializer,
    PanicAlertCreateSerializer,
    PanicAlertSerializer,
    PanicAlertUpdateSerializer,
    RegisterSerializer,
    RideHistorySerializer,
)


def tokens_for(user) -> dict:
    """Builds an access/refresh pair for a user (Module 3 — JWT auth)."""
    refresh = RefreshToken.for_user(user)
    return {"access": str(refresh.access_token), "refresh": str(refresh)}


class RegisterView(APIView):
    """Creates a driver account and returns a JWT pair."""

    permission_classes = [AllowAny]

    def post(self, request):
        serializer = RegisterSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = serializer.save()
        return Response(
            {"user": {"username": user.username, "email": user.email}, **tokens_for(user)},
            status=201,
        )


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
        # Operators list without a driver login; a driver must be authenticated to fire.
        # PRODUÇÃO: proteger o GET/PATCH com login/âmbito de operador.
        if self.request.method == "POST":
            return [IsAuthenticated()]
        return [AllowAny()]

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

    permission_classes = [AllowAny]  # PRODUÇÃO: autenticação de operador.

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
