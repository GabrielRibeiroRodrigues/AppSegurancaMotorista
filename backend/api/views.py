from rest_framework import generics
from rest_framework.permissions import AllowAny
from rest_framework.response import Response
from rest_framework.views import APIView
from rest_framework_simplejwt.tokens import RefreshToken

from .models import DriverProfile, RideHistory
from .serializers import (
    DriverProfileSerializer,
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
