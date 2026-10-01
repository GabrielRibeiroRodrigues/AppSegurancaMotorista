from django.contrib import admin

from .models import DriverProfile, RideHistory


@admin.register(DriverProfile)
class DriverProfileAdmin(admin.ModelAdmin):
    list_display = ("device_id", "target_per_km", "minimum_per_km", "voice_enabled", "updated_at")
    search_fields = ("device_id",)


@admin.register(RideHistory)
class RideHistoryAdmin(admin.ModelAdmin):
    list_display = (
        "source",
        "gross_price",
        "distance_km",
        "time_minutes",
        "net_profit",
        "classification",
        "accepted",
        "captured_at",
    )
    list_filter = ("source", "classification", "accepted")
    search_fields = ("driver__device_id",)
    date_hierarchy = "captured_at"
