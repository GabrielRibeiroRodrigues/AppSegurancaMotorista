from django.contrib.auth.models import User
from rest_framework import status
from rest_framework.test import APITestCase

from .models import DriverProfile, RideHistory


class AuthTests(APITestCase):
    def test_register_creates_user_profile_and_tokens(self):
        response = self.client.post(
            "/api/auth/register/",
            {"username": "motorista1", "password": "SenhaForte123"},
            format="json",
        )
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertIn("access", response.data)
        self.assertIn("refresh", response.data)
        self.assertTrue(User.objects.filter(username="motorista1").exists())
        self.assertTrue(DriverProfile.objects.filter(user__username="motorista1").exists())

    def test_login_returns_tokens(self):
        User.objects.create_user(username="joao", password="SenhaForte123")
        response = self.client.post(
            "/api/auth/login/",
            {"username": "joao", "password": "SenhaForte123"},
            format="json",
        )
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn("access", response.data)

    def test_rides_require_authentication(self):
        response = self.client.get("/api/rides/")
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)


class RideApiTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(username="ana", password="SenhaForte123")
        self.client.force_authenticate(user=self.user)

    def test_create_ride_links_to_authenticated_driver(self):
        response = self.client.post("/api/rides/", self._ride_payload(), format="json")
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(RideHistory.objects.count(), 1)
        self.assertEqual(RideHistory.objects.first().driver.user, self.user)

    def test_list_only_returns_own_rides(self):
        self.client.post("/api/rides/", self._ride_payload(), format="json")

        other = User.objects.create_user(username="outro", password="SenhaForte123")
        self.client.force_authenticate(user=other)
        self.client.post("/api/rides/", self._ride_payload(), format="json")

        self.client.force_authenticate(user=self.user)
        response = self.client.get("/api/rides/")
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 1)

    def test_profile_get_then_update(self):
        get_response = self.client.get("/api/profile/")
        self.assertEqual(get_response.status_code, status.HTTP_200_OK)

        payload = {
            "fuel_price_per_liter": "6.50",
            "km_per_liter": "10.00",
            "maintenance_cost_per_km": "0.30",
            "target_per_km": "2.00",
            "minimum_per_km": "1.30",
            "voice_enabled": False,
        }
        put_response = self.client.put("/api/profile/", payload, format="json")
        self.assertEqual(put_response.status_code, status.HTTP_200_OK)
        profile = DriverProfile.objects.get(user=self.user)
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
