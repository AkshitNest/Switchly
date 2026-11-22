package live.switchly.api.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTest {

    @ParameterizedTest
    @CsvSource({"ADMIN,ADMIN,true","ADMIN,EMPLOYEE,true","EMPLOYEE,EMPLOYEE,true","EMPLOYEE,ADMIN,false"})
    void permissions(Role role, Role required, boolean allowed) {
        assertThat(role.isAtLeast(required)).isEqualTo(allowed);
        assertThat(role.isAtLeast(null)).isFalse();
    }
}
