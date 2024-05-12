package proc;

import anno.GenClass;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import values.MappedValue;
import values.inner.FieldInfo;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

@AutoService(Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class Generate extends AbstractProcessor {

    private MappedValue mappedValue = new MappedValue();


    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(GenClass.class.getName());
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        Set<? extends Element> els = roundEnv.getElementsAnnotatedWith(GenClass.class);
        for (Element el : els) {
            List<? extends AnnotationMirror> annotationMirrors = el.getAnnotationMirrors();
            for (AnnotationMirror annotationMirror : annotationMirrors) {
                Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues = annotationMirror.getElementValues();
                genValues(elementValues, mappedValue);

            }
//            // 테스트 로깅
//            // 아직 컴파일이 되지 않았기 때문에 리플렉션으로 값을 읽어야 한다.
//            try {
//                Field field = mappedValue.getClass().getDeclaredField("field");
//                field.setAccessible(true);
//                Object value = field.get(mappedValue);
//                note("value: " + value.toString());
//                Field testField = mappedValue.getClass().getDeclaredField("testField");
//                testField.setAccessible(true);
//                Object testFieldValue = testField.get(mappedValue);
//                note("value: " + testFieldValue);
//
//            } catch (NoSuchFieldException | IllegalAccessException e) {
//                throw new RuntimeException(e);
//            }

            // 클래스, 필드 생성
            List<FieldSpec> fields = new ArrayList<>();
            try {
                Field fieldField = mappedValue.getClass().getDeclaredField("field");
                fieldField.setAccessible(true);
                Object o = fieldField.get(mappedValue);
                for (Object object : ((List<?>) o)) {
                    Field modifierField = object.getClass().getDeclaredField("modifier");
                    Field nameField = object.getClass().getDeclaredField("name");
                    Field typeField = object.getClass().getDeclaredField("type");
                    modifierField.setAccessible(true);
                    nameField.setAccessible(true);
                    typeField.setAccessible(true);

                    String modifier = (String) modifierField.get(object);
                    String name = (String) nameField.get(object);
                    Class<?> type = (Class<?>) typeField.get(object);

                    fields.add(FieldSpec.builder(type, name)
                            .addModifiers(Modifier.valueOf(modifier.toUpperCase()))
                            .build());
                }
                TypeSpec typeSpec = TypeSpec.classBuilder("testClass")
                        .addModifiers(Modifier.PUBLIC)
                        .addFields(fields)
                        .build();

                Filer filer = processingEnv.getFiler();
                ClassName className = ClassName.get((TypeElement) el);
                JavaFile.builder(className.packageName(), typeSpec)
                        .build()
                        .writeTo(filer);

            } catch (NoSuchFieldException | IllegalAccessException | IOException e) {
                throw new RuntimeException(e);
            }
        }

        return true;
    }

    private <T> void genValues(Map<? extends ExecutableElement, ? extends AnnotationValue> elementValues, T targetInstance) {
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : elementValues.entrySet()) {
            ExecutableElement key = entry.getKey();
            AnnotationValue value = entry.getValue();
            // value 가 리스트일 경우
            if (value.getValue() instanceof List<?>) {
                // 리스트 처리
                if (!((List<?>) value.getValue()).isEmpty() && ((List<?>) value.getValue()).get(0) instanceof AnnotationMirror) {
                    settingListWhenAnnotationMirror(targetInstance, key, value);
                    continue;
                }
            }
            // 값 세팅
            setValue(targetInstance, key, value);
        }
    }

    private <T> void setValue(T targetInstance, ExecutableElement key, AnnotationValue value) {
        Object settingValue = null;
        Field settingField;
        try {
            settingField = targetInstance.getClass().getDeclaredField(key.getSimpleName().toString());
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        if (value.getValue() instanceof TypeMirror) {

            TypeMirror typeMirror = (TypeMirror) value.getValue();
            // 원시타입일 경우
            if (typeMirror.getKind().isPrimitive()) {
                Class<?> clazz = getaClass((PrimitiveType) typeMirror);
                settingValue = clazz;
            }

            if (value.getValue() instanceof DeclaredType) {
                DeclaredType declaredType = (DeclaredType) typeMirror;
                Element classEl = declaredType.asElement();

                Class<?> clazz;
                try {
                    clazz = Class.forName(classEl.toString());
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
                settingValue = clazz;
            }

        } else if (value.getValue() instanceof VariableElement) {
            // enum 일 경우
            VariableElement variableElement = (VariableElement) value.getValue();
            settingValue = variableElement.toString();


        } else {
            settingValue = settingField.getType().cast(value.getValue());
        }
        try {
            settingField.setAccessible(true);
            settingField.set(targetInstance, settingValue);
        } catch (IllegalAccessException e) {
            note("erorr: tslekoae2");
        }
    }

    private <T> void settingListWhenAnnotationMirror(T targetInstance, ExecutableElement key,
                                                     AnnotationValue value) {
        // filed[] 라면
        // filed[] 는 List<FieldInfo> 로 변환되어야 한다.
        // 현재 targetInstance 의 검사중인 필드는 List<FieldInfo> 다.
        try {
            Field field = targetInstance.getClass().getDeclaredField(key.getSimpleName().toString());
            field.setAccessible(true);
            if (field.get(targetInstance) == null) {
                field.set(targetInstance, new ArrayList<>());
            }
            List<Object> list = (List<Object>) field.get(targetInstance);
            Class<?> actualClass = getClassFromGeneric(field);

            for (Object o : (List<?>) value.getValue()) {
                Object curO = actualClass.getConstructor().newInstance();
                AnnotationMirror curMirror = (AnnotationMirror) o;

                Map<? extends ExecutableElement, ? extends AnnotationValue> curValues = curMirror.getElementValues();
                genValues(curValues, actualClass.cast(curO));
                list.add(curO);
            }

        } catch (NoSuchFieldException | IllegalAccessException | InvocationTargetException | InstantiationException |
                 NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private static Class<?> getClassFromGeneric(Field field) {
        /* --- 제네릭타입의 Class 객체 가져오기 --- */
        Type genericType = field.getGenericType();
        Type[] actualTypeArguments = ((ParameterizedType) genericType).getActualTypeArguments();
        Type actualTypeArgument = actualTypeArguments[0];
        Class<?> actualClass = (Class<?>) actualTypeArgument;
        /* --- 제네릭타입의 Class 객체 가져오기 --- */
        return actualClass;
    }

    private static Class<?> getaClass(PrimitiveType typeMirror) {
        PrimitiveType primitiveType = typeMirror;
        TypeKind kind = primitiveType.getKind();
        Class<?> clazz = null;
        switch (kind) {
            case INT -> clazz = int.class;
            case BOOLEAN -> clazz = boolean.class;
            case BYTE -> clazz = byte.class;
            case CHAR -> clazz = char.class;
            case SHORT -> clazz = short.class;
            case LONG -> clazz = long.class;
            case FLOAT -> clazz = float.class;
            case DOUBLE -> clazz = double.class;
            case VOID -> clazz = void.class;
        }
        return clazz;
    }


    private void note(String message) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, message);
    }
}
