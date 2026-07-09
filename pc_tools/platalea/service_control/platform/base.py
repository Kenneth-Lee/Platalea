from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol

from ..models import PrivilegedUnitSpec, SupervisorStatus, UserServerUnitSpec


@dataclass(frozen=True)
class PowerAction:
    name: str


class PlatformSupervisorAdapter(Protocol):
    def install_privileged_unit(self, spec: PrivilegedUnitSpec) -> None:
        """Install and enable the privileged broker unit."""

    def install_user_server_unit(self, spec: UserServerUnitSpec) -> None:
        """Install and enable the owner-scoped user server unit."""

    def uninstall_all_units(self, *, ignore_missing: bool = True) -> None:
        """Remove managed units for the control plane."""

    def query_status(self) -> SupervisorStatus:
        """Summarize managed unit status."""

    def run_power_action(self, action: PowerAction) -> None:
        """Execute a whitelisted system power action."""
