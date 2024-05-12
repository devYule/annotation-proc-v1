package anno;



import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.lang.model.element.Modifier;

@Retention(RetentionPolicy.SOURCE)
public @interface Field {

    Modifier modifier();
    Class<?> type();
    String name();

}
