import django.db.models.deletion
from django.db import migrations, models


class Migration(migrations.Migration):

    initial = True

    dependencies = []

    operations = [
        migrations.CreateModel(
            name="DriverProfile",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                ("device_id", models.CharField(db_index=True, max_length=64, unique=True)),
                ("fuel_price_per_liter", models.DecimalField(decimal_places=2, default=6.0, max_digits=8)),
                ("km_per_liter", models.DecimalField(decimal_places=2, default=12.0, max_digits=6)),
                ("maintenance_cost_per_km", models.DecimalField(decimal_places=2, default=0.25, max_digits=6)),
                ("target_per_km", models.DecimalField(decimal_places=2, default=1.8, max_digits=6)),
                ("minimum_per_km", models.DecimalField(decimal_places=2, default=1.2, max_digits=6)),
                ("voice_enabled", models.BooleanField(default=True)),
                ("created_at", models.DateTimeField(auto_now_add=True)),
                ("updated_at", models.DateTimeField(auto_now=True)),
            ],
        ),
        migrations.CreateModel(
            name="RideHistory",
            fields=[
                ("id", models.BigAutoField(auto_created=True, primary_key=True, serialize=False, verbose_name="ID")),
                (
                    "source",
                    models.CharField(
                        choices=[
                            ("UBER", "Uber"),
                            ("NINETY_NINE", "99"),
                            ("INDRIVE", "inDrive"),
                            ("SIMULATOR", "Simulador"),
                            ("UNKNOWN", "Desconhecido"),
                        ],
                        default="UNKNOWN",
                        max_length=16,
                    ),
                ),
                ("gross_price", models.DecimalField(decimal_places=2, max_digits=10)),
                ("distance_km", models.DecimalField(decimal_places=2, max_digits=8)),
                ("time_minutes", models.PositiveIntegerField()),
                ("net_profit", models.DecimalField(decimal_places=2, max_digits=10)),
                ("gross_per_km", models.DecimalField(decimal_places=2, default=0, max_digits=10)),
                ("gross_per_hour", models.DecimalField(decimal_places=2, default=0, max_digits=10)),
                (
                    "classification",
                    models.CharField(
                        choices=[("GREEN", "Verde"), ("YELLOW", "Amarela"), ("RED", "Vermelha")],
                        max_length=8,
                    ),
                ),
                ("accepted", models.BooleanField(default=False)),
                ("captured_at", models.DateTimeField()),
                ("created_at", models.DateTimeField(auto_now_add=True)),
                (
                    "driver",
                    models.ForeignKey(
                        on_delete=django.db.models.deletion.CASCADE,
                        related_name="rides",
                        to="api.driverprofile",
                    ),
                ),
            ],
            options={
                "verbose_name_plural": "Ride histories",
                "ordering": ["-captured_at"],
            },
        ),
    ]
