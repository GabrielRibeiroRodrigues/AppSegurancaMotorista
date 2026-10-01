from rest_framework import generics, status
from rest_framework.exceptions import ValidationError
from rest_framework.response import Response
from rest_framework.views import APIView

from .models import DriverProfile, RideHistory
from .serializers import DriverProfileSerializer, RideHistorySerializer

DEVICE_HEADER = "X-Device-Id"


def resolve_device_id(request) -> str:
    """Reads the per-install device id, rejecting requests without one."""
    device_id = request.headers.get(DEVICE_HEADER)
    if not device_id:
        raise ValidationError({"detail": f"Missing {DEVICE_HEADER} header."})
    return device_id


class RideListCreateView(generics.ListCreateAPIView):
    """GET lists this device's rides; POST backs up a captured ride (Module F)."""

    serializer_class = RideHistorySerializer

    def get_queryset(self):
        device_id = resolve_device_id(self.request)
        return RideHistory.objects.filter(driver__device_id=device_id)

    def perform_create(self, serializer):
        device_id = resolve_device_id(self.request)
        driver, _ = DriverProfile.objects.get_or_create(device_id=device_id)
        serializer.save(driver=driver)


class DriverProfileView(APIView):
    """GET returns (creating defaults if needed) and PUT updates the config."""

    def get(self, request):
        device_id = resolve_device_id(request)
        profile, _ = DriverProfile.objects.get_or_create(device_id=device_id)
        return Response(DriverProfileSerializer(profile).data)

    def put(self, request):
        device_id = resolve_device_id(request)
        profile, _ = DriverProfile.objects.get_or_create(device_id=device_id)
        serializer = DriverProfileSerializer(profile, data=request.data)
        serializer.is_valid(raise_exception=True)
        serializer.save()
        return Response(serializer.data, status=status.HTTP_200_OK)
