from rest_framework import status
from rest_framework.test import APITestCase

from .models import DriverProfile, RideHistory

DEVICE = "test-device-123"
HEADER = {"HTTP_X_DEVICE_ID": DEVICE}


class RideApiTests(APITestCase):
    def test_create_ride_requires_device_header(self):
        response = self.client.post("/api/rides/", self._ride_payload(), format="json")
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_create_ride_persists_and_links_driver(self):
        response = self.client.post("/api/rides/", self._ride_payload(), format="json", **HEADER)
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(RideHistory.objects.count(), 1)
        self.assertTrue(DriverProfile.objects.filter(device_id=DEVICE).exists())

    def test_list_only_returns_own_rides(self):
        self.client.post("/api/rides/", self._ride_payload(), format="json", **HEADER)
        other = {"HTTP_X_DEVICE_ID": "another-device"}
        self.client.post("/api/rides/", self._ride_payload(), format="json", **other)

        response = self.client.get("/api/rides/", **HEADER)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 1)

    def test_profile_get_then_update(self):
        get_response = self.client.get("/api/profile/", **HEADER)
        self.assertEqual(get_response.status_code, status.HTTP_200_OK)

        payload = {
            "fuel_price_per_liter": "6.50",
            "km_per_liter": "10.00",
            "maintenance_cost_per_km": "0.30",
            "target_per_km": "2.00",
            "minimum_per_km": "1.30",
            "voice_enabled": False,
        }
        put_response = self.client.put("/api/profile/", payload, format="json", **HEADER)
        self.assertEqual(put_response.status_code, status.HTTP_200_OK)
        profile = DriverProfile.objects.get(device_id=DEVICE)
        self.assertFalse(profile.voice_enabled)
        self.assertEqual(str(profile.target_per_km), "2.00")

    @staticmethod
    def _ride_payload():
        return {
            "source": "UBER",
            "gross_price": "18.50",
            "distance_km": "6.00",
            "time_minutes": 12,
            "net_profit": "14.00",
            "gross_per_km": "3.08",
            "gross_per_hour": "92.50",
            "classification": "GREEN",
            "accepted": True,
            "captured_at": "2026-09-30T12:00:00Z",
        }
