package entity;

public interface EntityVisitor<T> {
    public T visit(DefinedVariable var);
    public T visit(DefinedFunction func);
    public T visit(Constant c);
}
