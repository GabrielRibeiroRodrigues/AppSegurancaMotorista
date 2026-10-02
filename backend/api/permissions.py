from rest_framework.permissions import BasePermission


class IsOperator(BasePermission):
    """
    Allows access only to operators of the Central de Operações.

    An operator is any authenticated user flagged as staff (`is_staff`) — they are
    created/promoted in the Django admin. Regular drivers are never staff, so they
    cannot read other drivers' panic alerts or act on them.
    """

    message = "Apenas operadores da central podem acessar este recurso."

    def has_permission(self, request, view) -> bool:
        user = request.user
        return bool(user and user.is_authenticated and user.is_staff)
