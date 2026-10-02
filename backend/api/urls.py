from django.urls import path
from rest_framework_simplejwt.views import TokenObtainPairView, TokenRefreshView

from .views import (
    DriverProfileView,
    PanicAlertDetailView,
    PanicAlertListCreateView,
    RegisterView,
    RideListCreateView,
)

urlpatterns = [
    # Auth (Module 3 — JWT)
    path("api/auth/register/", RegisterView.as_view(), name="auth-register"),
    path("api/auth/login/", TokenObtainPairView.as_view(), name="auth-login"),
    path("api/auth/refresh/", TokenRefreshView.as_view(), name="auth-refresh"),

    # Data
    path("api/rides/", RideListCreateView.as_view(), name="ride-list-create"),
    path("api/profile/", DriverProfileView.as_view(), name="driver-profile"),

    # Panic alerts (DesafioMaker — Central de Operações)
    path("api/alerts/", PanicAlertListCreateView.as_view(), name="alert-list-create"),
    path("api/alerts/<int:pk>/", PanicAlertDetailView.as_view(), name="alert-detail"),
]
