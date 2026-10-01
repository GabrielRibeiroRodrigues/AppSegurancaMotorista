from django.urls import path

from .views import DriverProfileView, RideListCreateView

urlpatterns = [
    path("api/rides/", RideListCreateView.as_view(), name="ride-list-create"),
    path("api/profile/", DriverProfileView.as_view(), name="driver-profile"),
]
