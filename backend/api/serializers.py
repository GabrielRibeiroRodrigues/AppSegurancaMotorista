from rest_framework import serializers

from .models import DriverProfile, RideHistory


class DriverProfileSerializer(serializers.ModelSerializer):
    class Meta:
        model = DriverProfile
        fields = [
            "fuel_price_per_liter",
            "km_per_liter",
            "maintenance_cost_per_km",
            "target_per_km",
            "minimum_per_km",
            "voice_enabled",
        ]


class RideHistorySerializer(serializers.ModelSerializer):
    class Meta:
        model = RideHistory
        fields = [
            "id",
            "source",
            "gross_price",
            "distance_km",
            "time_minutes",
            "net_profit",
            "gross_per_km",
            "gross_per_hour",
            "classification",
            "accepted",
            "captured_at",
        ]
        read_only_fields = ["id"]
