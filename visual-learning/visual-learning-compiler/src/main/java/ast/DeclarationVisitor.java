package ast;

public interface DeclarationVisitor<T> {
    public T visit(TypedefNode typedef);
}
