package live.switchly.api.model;

public enum Role {
    ADMIN(2),
    EMPLOYEE(1);
    private final int rank;

    Role(int rank) {
        this.rank = rank;
    }

    public int getRank() {
        return rank;
    }

    public boolean isAtLeast(Role requiredRole) {
        if (requiredRole == null) {
            return false;
        }
        return this.rank >= requiredRole.rank;
    }
}



