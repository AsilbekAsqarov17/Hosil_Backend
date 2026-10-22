package uz.hosil.platform.identity.domain;

import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Objects;

/**
 * Custom Hibernate UserType to support PostgreSQL's native 'inet' type
 * while falling back cleanly to VARCHAR on in-memory databases like H2 for testing.
 */
public class PostgresInetUserType implements UserType<String> {

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<String> returnedClass() {
        return String.class;
    }

    @Override
    public boolean equals(String x, String y) {
        return Objects.equals(x, y);
    }

    @Override
    public int hashCode(String x) {
        return x != null ? x.hashCode() : 0;
    }

    @Override
    public String nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException {
        return rs.getString(position);
    }

    @Override
    public void nullSafeSet(PreparedStatement st, String value, int index, SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
            return;
        }

        boolean isPostgres = session.getJdbcServices().getDialect() instanceof PostgreSQLDialect;
        if (isPostgres) {
            try {
                Object pgObject = Class.forName("org.postgresql.util.PGobject").getDeclaredConstructor().newInstance();
                pgObject.getClass().getMethod("setType", String.class).invoke(pgObject, "inet");
                pgObject.getClass().getMethod("setValue", String.class).invoke(pgObject, value);
                st.setObject(index, pgObject, Types.OTHER);
            } catch (Exception e) {
                st.setObject(index, value, Types.OTHER);
            }
        } else {
            st.setString(index, value);
        }
    }

    @Override
    public String deepCopy(String value) {
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(String value) {
        return value;
    }

    @Override
    public String assemble(Serializable cached, Object owner) {
        return (String) cached;
    }
}
