from django.db import models


class DriverProfile(models.Model):
    """Driver configuration mirrored from the mobile app (Module F).

    Identified by a per-install device id so the MVP needs no login.
    """

    device_id = models.CharField(max_length=64, unique=True, db_index=True)
    fuel_price_per_liter = models.DecimalField(max_digits=8, decimal_places=2, default=6.00)
    km_per_liter = models.DecimalField(max_digits=6, decimal_places=2, default=12.00)
    maintenance_cost_per_km = models.DecimalField(max_digits=6, decimal_places=2, default=0.25)
    target_per_km = models.DecimalField(max_digits=6, decimal_places=2, default=1.80)
    minimum_per_km = models.DecimalField(max_digits=6, decimal_places=2, default=1.20)
    voice_enabled = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self) -> str:
        return f"DriverProfile({self.device_id})"


class RideHistory(models.Model):
    """A captured ride offer and its evaluation (Module F)."""

    class Source(models.TextChoices):
        UBER = "UBER", "Uber"
        NINETY_NINE = "NINETY_NINE", "99"
        INDRIVE = "INDRIVE", "inDrive"
        SIMULATOR = "SIMULATOR", "Simulador"
        UNKNOWN = "UNKNOWN", "Desconhecido"

    class Classification(models.TextChoices):
        GREEN = "GREEN", "Verde"
        YELLOW = "YELLOW", "Amarela"
        RED = "RED", "Vermelha"

    driver = models.ForeignKey(
        DriverProfile,
        on_delete=models.CASCADE,
        related_name="rides",
    )
    source = models.CharField(max_length=16, choices=Source.choices, default=Source.UNKNOWN)
    gross_price = models.DecimalField(max_digits=10, decimal_places=2)
    distance_km = models.DecimalField(max_digits=8, decimal_places=2)
    time_minutes = models.PositiveIntegerField()
    net_profit = models.DecimalField(max_digits=10, decimal_places=2)
    gross_per_km = models.DecimalField(max_digits=10, decimal_places=2, default=0)
    gross_per_hour = models.DecimalField(max_digits=10, decimal_places=2, default=0)
    classification = models.CharField(max_length=8, choices=Classification.choices)
    accepted = models.BooleanField(default=False)
    captured_at = models.DateTimeField()
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["-captured_at"]
        verbose_name_plural = "Ride histories"

    def __str__(self) -> str:
        return f"{self.source} R${self.gross_price} ({self.classification})"
