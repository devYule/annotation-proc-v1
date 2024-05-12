package values.inner;



public class FieldInfo {
    private String modifier;
    private String name;
    private Class<?> type;

    public String getModifier() {
        return modifier;
    }

    public void setModifier(String modifier) {
        this.modifier = modifier;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Class<?> getType() {
        return type;
    }

    public void setType(Class<?> type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return "FieldInfo{" +
               "modifier='" + modifier + '\'' +
               ", name='" + name + '\'' +
               ", type=" + type +
               '}';
    }
}
