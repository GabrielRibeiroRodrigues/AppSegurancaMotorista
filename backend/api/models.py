from django.contrib.auth.models import User
from django.db import models


class DriverProfile(models.Model):
    """Driver configuration mirrored from the mobile app (Module F).

    Linked to the authenticated Django user so data follows the driver across
    devices (Module 3 — JWT auth).
    """

    user = models.OneToOneField(
        User,
        on_delete=models.CASCADE,
        related_name="driver_profile",
    )
    fuel_price_per_liter = models.DecimalField(max_digits=8, decimal_places=2, default=6.00)
    km_per_liter = models.DecimalField(max_digits=6, decimal_places=2, default=12.00)
    maintenance_cost_per_km = models.DecimalField(max_digits=6, decimal_places=2, default=0.25)
    target_per_km = models.DecimalField(max_digits=6, decimal_places=2, default=1.80)
    minimum_per_km = models.DecimalField(max_digits=6, decimal_places=2, default=1.20)
    target_per_hour = models.DecimalField(max_digits=7, decimal_places=2, default=30.00)
    daily_goal = models.DecimalField(max_digits=10, decimal_places=2, default=300.00)
    voice_enabled = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self) -> str:
        return f"DriverProfile({self.user.username})"


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
        RISK_RED = "RISK_RED", "Área de risco"

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
    pickup = models.CharField(max_length=240, blank=True, default="")
    dropoff = models.CharField(max_length=240, blank=True, default="")
    captured_at = models.DateTimeField()
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ["-captured_at"]
        verbose_name_plural = "Ride histories"

    def __str__(self) -> str:
        return f"{self.source} R${self.gross_price} ({self.classification})"


class PanicAlert(models.Model):
    """Emergency alert raised by a driver (DesafioMaker — Botão de Pânico).

    Fired by a hidden trigger or a trigger phrase; carries location and the
    captured transcript, and is handled by an operator in the Central de Operações.
    """

    class Status(models.TextChoices):
        ATIVO = "ativo", "Ativo"
        EM_ATENDIMENTO = "em_atendimento", "Em atendimento"
        ENCERRADO = "encerrado", "Encerrado"

    class OperatorAction(models.TextChoices):
        ACIONAR_POLICIA = "acionar_policia", "Acionar polícia"
        CONTATO_ATIVO = "contato_ativo", "Contato ativo"
        FALSO_POSITIVO = "falso_positivo", "Falso positivo"

    driver = models.ForeignKey(
        DriverProfile,
        on_delete=models.CASCADE,
        related_name="alerts",
    )
    timestamp = models.DateTimeField(help_text="Quando o motorista disparou o alerta.")
    received_at = models.DateTimeField(auto_now_add=True)
    lat = models.FloatField()
    lng = models.FloatField()
    transcript = models.TextField(blank=True, default="")
    status = models.CharField(max_length=16, choices=Status.choices, default=Status.ATIVO)
    operator_action = models.CharField(
        max_length=16,
        choices=OperatorAction.choices,
        null=True,
        blank=True,
    )
    is_test = models.BooleanField(default=False)

    class Meta:
        ordering = ["-received_at"]

    def __str__(self) -> str:
        return f"Alerta {self.pk} ({self.status}) — {self.driver.user.username}"
