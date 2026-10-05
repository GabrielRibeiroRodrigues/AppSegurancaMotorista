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


class PanicAlertTests(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(username="motorista_alerta", password="SenhaForte123")

    def _payload(self, is_test=False):
        return {
            "timestamp": "2026-10-02T12:00:00Z",
            "lat": -21.37,
            "lng": -46.52,
            "transcript": "ativar protecao agora",
            "is_test": is_test,
        }

    def test_fire_alert_requires_authentication(self):
        response = self.client.post("/api/alerts/", self._payload(), format="json")
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_fire_alert_creates_active_alert(self):
        self.client.force_authenticate(user=self.user)
        response = self.client.post("/api/alerts/", self._payload(), format="json")
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(response.data["status"], "ativo")
        self.assertEqual(response.data["driverId"], "motorista_alerta")
        self.assertEqual(response.data["location"], {"lat": -21.37, "lng": -46.52})
        # Defaults to APP when the origin is not sent.
        self.assertEqual(response.data["origin"], "APP")

    def test_fire_alert_records_physical_button_origin(self):
        self.client.force_authenticate(user=self.user)
        payload = {**self._payload(), "origin": "BOTAO_PANICO"}
        response = self.client.post("/api/alerts/", payload, format="json")
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(response.data["origin"], "BOTAO_PANICO")

    def test_list_alerts_requires_operator(self):
        self.client.force_authenticate(user=self.user)
        self.client.post("/api/alerts/", self._payload(), format="json")

        # Anonymous: denied.
        self.client.force_authenticate(user=None)
        self.assertEqual(self.client.get("/api/alerts/").status_code, status.HTTP_401_UNAUTHORIZED)

        # A regular driver (not staff): forbidden, even seeing their own alert list.
        self.client.force_authenticate(user=self.user)
        self.assertEqual(self.client.get("/api/alerts/").status_code, status.HTTP_403_FORBIDDEN)

        # An operator (is_staff): allowed.
        operator = User.objects.create_user(
            username="operador1", password="SenhaForte123", is_staff=True
        )
        self.client.force_authenticate(user=operator)
        response = self.client.get("/api/alerts/")
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(len(response.data), 1)

    def test_operator_action_requires_operator(self):
        self.client.force_authenticate(user=self.user)
        created = self.client.post("/api/alerts/", self._payload(), format="json")
        alert_id = created.data["id"]

        # A regular driver cannot act on an alert.
        denied = self.client.patch(
            f"/api/alerts/{alert_id}/",
            {"operator_action": "acionar_policia"},
            format="json",
        )
        self.assertEqual(denied.status_code, status.HTTP_403_FORBIDDEN)

        operator = User.objects.create_user(
            username="operador2", password="SenhaForte123", is_staff=True
        )
        self.client.force_authenticate(user=operator)
        response = self.client.patch(
            f"/api/alerts/{alert_id}/",
            {"operator_action": "acionar_policia"},
            format="json",
        )
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data["operatorAction"], "acionar_policia")
        self.assertEqual(response.data["status"], "em_atendimento")


class SessionSecurityTests(APITestCase):
    def test_me_reports_operator_flag(self):
        driver = User.objects.create_user(username="driver_me", password="SenhaForte123")
        operator = User.objects.create_user(
            username="op_me", password="SenhaForte123", is_staff=True
        )
        self.client.force_authenticate(user=driver)
        self.assertFalse(self.client.get("/api/auth/me/").data["is_operator"])
        self.client.force_authenticate(user=operator)
        self.assertTrue(self.client.get("/api/auth/me/").data["is_operator"])

    def test_logout_blacklists_refresh_token(self):
        User.objects.create_user(username="sai", password="SenhaForte123")
        login = self.client.post(
            "/api/auth/login/",
            {"username": "sai", "password": "SenhaForte123"},
            format="json",
        )
        refresh = login.data["refresh"]
        access = login.data["access"]

        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {access}")
        logout = self.client.post("/api/auth/logout/", {"refresh": refresh}, format="json")
        self.assertEqual(logout.status_code, 205)

        # The blacklisted refresh token can no longer mint a new access token.
        self.client.credentials()
        again = self.client.post("/api/auth/refresh/", {"refresh": refresh}, format="json")
        self.assertEqual(again.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_health_is_open(self):
        response = self.client.get("/api/health/")
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.data["status"], "ok")
