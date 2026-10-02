from django.contrib.auth.models import User
from django.contrib.auth.password_validation import validate_password
from rest_framework import serializers
from rest_framework_simplejwt.serializers import TokenObtainPairSerializer

from .models import DriverProfile, PanicAlert, RideHistory


def add_claims(token, user):
    """Embeds identity + role in the JWT so the signaling server can authorize
    WebRTC connections (only operators may watch) without a DB lookup."""
    token["username"] = user.username
    token["is_staff"] = bool(user.is_staff)
    return token


class CopilotoTokenObtainPairSerializer(TokenObtainPairSerializer):
    """Login token with the extra claims used by the signaling server."""

    @classmethod
    def get_token(cls, user):
        return add_claims(super().get_token(user), user)


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


class PanicAlertSerializer(serializers.ModelSerializer):
    """Read shape consumed by the Central de Operações (camelCase + nested location)."""

    driverId = serializers.CharField(source="driver.user.username", read_only=True)
    receivedAt = serializers.DateTimeField(source="received_at", read_only=True)
    operatorAction = serializers.CharField(source="operator_action", read_only=True)
    isTest = serializers.BooleanField(source="is_test", read_only=True)
    location = serializers.SerializerMethodField()

    class Meta:
        model = PanicAlert
        fields = [
            "id",
            "driverId",
            "timestamp",
            "receivedAt",
            "location",
            "transcript",
            "status",
            "operatorAction",
            "isTest",
        ]

    def get_location(self, obj):
        return {"lat": obj.lat, "lng": obj.lng}


class PanicAlertCreateSerializer(serializers.Serializer):
    """Write shape sent by the driver app when an alert fires."""

    timestamp = serializers.DateTimeField()
    lat = serializers.FloatField()
    lng = serializers.FloatField()
    transcript = serializers.CharField(allow_blank=True, default="")
    is_test = serializers.BooleanField(default=False)


class PanicAlertUpdateSerializer(serializers.Serializer):
    """Operator action from the Central de Operações."""

    operator_action = serializers.ChoiceField(choices=PanicAlert.OperatorAction.choices)
    status = serializers.ChoiceField(
        choices=PanicAlert.Status.choices,
        required=False,
    )
