package ch.passenger.kinterest.util.json;

/**
 * Created by svd on 19/12/13.
 */
public class EnumDecoder {
    public static Object decode(Class cls, String name) {
        if(cls.isEnum()) {
            Class<Enum> ec = cls;
            return Enum.valueOf(ec, name);
        }
        return null;
    }

    public static Enum[] values(Class cls) {
        Class<Enum> ec = cls;

        return ec.getEnumConstants();
    }
}
