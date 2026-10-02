from django.contrib import admin
from django.utils.html import format_html

from .models import DriverProfile, RideHistory


class RideHistoryInline(admin.TabularInline):
    """Shows all of a driver's rides directly on the driver page."""

    model = RideHistory
    extra = 0
    can_delete = False
    ordering = ("-captured_at",)
    fields = (
        "captured_at",
        "source",
        "dropoff",
        "gross_price",
        "distance_km",
        "time_minutes",
        "net_profit",
        "classification",
        "accepted",
    )
    readonly_fields = fields

    def has_add_permission(self, request, obj=None):
        return False


@admin.register(DriverProfile)
class DriverProfileAdmin(admin.ModelAdmin):
    list_display = (
        "user",
        "rides_count",
        "target_per_km",
        "minimum_per_km",
        "target_per_hour",
        "daily_goal",
        "voice_enabled",
        "updated_at",
    )
    search_fields = ("user__username", "user__email")
    readonly_fields = ("created_at", "updated_at")
    fieldsets = (
        ("Motorista", {"fields": ("user",)}),
        ("Custos", {"fields": ("fuel_price_per_liter", "km_per_liter", "maintenance_cost_per_km")}),
        ("Metas", {"fields": ("target_per_km", "minimum_per_km", "target_per_hour", "daily_goal")}),
        ("Preferências", {"fields": ("voice_enabled",)}),
        ("Datas", {"fields": ("created_at", "updated_at")}),
    )
    inlines = [RideHistoryInline]

    @admin.display(description="Corridas")
    def rides_count(self, obj):
        return obj.rides.count()


@admin.register(RideHistory)
class RideHistoryAdmin(admin.ModelAdmin):
    list_display = (
        "captured_at",
        "driver_username",
        "source",
        "dropoff",
        "gross_price",
        "net_profit",
        "classification_badge",
        "accepted",
    )
    list_filter = ("source", "classification", "accepted", "captured_at")
    search_fields = ("driver__user__username", "dropoff", "pickup")
    date_hierarchy = "captured_at"
    ordering = ("-captured_at",)
    list_select_related = ("driver", "driver__user")

    @admin.display(description="Motorista", ordering="driver__user__username")
    def driver_username(self, obj):
        return obj.driver.user.username

    @admin.display(description="Classificação")
    def classification_badge(self, obj):
        colors = {
            "GREEN": "#16A34A",
            "YELLOW": "#D97706",
            "RED": "#DC2626",
            "RISK_RED": "#DC2626",
        }
        color = colors.get(obj.classification, "#6B7280")
        return format_html(
            '<b style="color:{}">{}</b>', color, obj.get_classification_display()
        )
