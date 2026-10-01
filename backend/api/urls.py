from django.urls import path
from rest_framework_simplejwt.views import TokenObtainPairView, TokenRefreshView

from .views import DriverProfileView, RegisterView, RideListCreateView

urlpatterns = [
    # Auth (Module 3 — JWT)
    path("api/auth/register/", RegisterView.as_view(), name="auth-register"),
    path("api/auth/login/", TokenObtainPairView.as_view(), name="auth-login"),
    path("api/auth/refresh/", TokenRefreshView.as_view(), name="auth-refresh"),

    # Data
    path("api/rides/", RideListCreateView.as_view(), name="ride-list-create"),
    path("api/profile/", DriverProfileView.as_view(), name="driver-profile"),
]
