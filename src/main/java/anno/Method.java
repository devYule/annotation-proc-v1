package anno;


import java.lang.reflect.Modifier;

public @interface Method {
    int modifier() default Modifier.PUBLIC;
    String name();

}
