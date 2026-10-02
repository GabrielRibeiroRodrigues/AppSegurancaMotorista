from django.contrib.auth.models import User
from django.contrib.auth.password_validation import validate_password
from rest_framework import serializers

from .models import DriverProfile, RideHistory


class RegisterSerializer(serializers.ModelSerializer):
    """Creates a Django user (and its DriverProfile) for a new driver."""

    password = serializers.CharField(write_only=True, validators=[validate_password])

    class Meta:
        model = User
        fields = ["username", "email", "password"]
        extra_kwargs = {"email": {"required": False}}

    def create(self, validated_data):
        user = User.objects.create_user(
            username=validated_data["username"],
            email=validated_data.get("email", ""),
            password=validated_data["password"],
        )
        DriverProfile.objects.get_or_create(user=user)
        return user


class DriverProfileSerializer(serializers.ModelSerializer):
    class Meta:
        model = DriverProfile
        fields = [
            "fuel_price_per_liter",
            "km_per_liter",
            "maintenance_cost_per_km",
            "target_per_km",
            "minimum_per_km",
            "target_per_hour",
            "daily_goal",
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
            "pickup",
            "dropoff",
            "captured_at",
        ]
        read_only_fields = ["id"]
