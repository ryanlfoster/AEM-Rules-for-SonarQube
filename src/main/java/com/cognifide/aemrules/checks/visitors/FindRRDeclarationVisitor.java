package com.cognifide.aemrules.checks.visitors;

import com.google.common.collect.Sets;
import org.sonar.plugins.java.api.tree.*;
import org.sonar.plugins.java.api.tree.Tree.Kind;

import java.util.Collection;
import java.util.Set;

/**
 * Finds all injector variable declarations. Used in method's bodies only.
 */
public class FindRRDeclarationVisitor extends BaseTreeVisitor {

	public static final String RESOURCE_RESOLVER_NAME = "org.apache.sling.api.resource.ResourceResolver";

	public static final String RESOURCE_RESOLVER_FACTORY = "org.apache.sling.api.resource.ResourceResolverFactory";

	private final Set<VariableTree> resourceResolvers;

	public FindRRDeclarationVisitor() {
		resourceResolvers = Sets.newHashSet();
	}

	public Collection<VariableTree> getDeclarations() {
		return resourceResolvers;
	}

	@Override
	public void visitAssignmentExpression(AssignmentExpressionTree tree) {
		if (isMethodInvocation(tree)) {
			MethodInvocationTree methodInvocation = (MethodInvocationTree) tree.expression();
			if (isManuallyCreatedResourceResolver(methodInvocation)) {
				IdentifierTree variable = (IdentifierTree) tree.variable();
				resourceResolvers.add((VariableTree) variable.symbol().declaration());
			} else if (isRR(methodInvocation) && methodInvocation.methodSelect().is(Kind.IDENTIFIER)) {
				MethodTree methodDeclaration = getMethodTree(methodInvocation);
				if (isManuallyCreatedResourceResolver(methodDeclaration)) {
					resourceResolvers.add((VariableTree) getDeclaration((IdentifierTree) tree.variable()));
				}
			}
		}
		super.visitAssignmentExpression(tree);
	}

	private boolean isManuallyCreatedResourceResolver(MethodTree methodDeclaration) {
		CheckIfRRCreatedManually rrCreatedManually = new CheckIfRRCreatedManually();
		methodDeclaration.accept(rrCreatedManually);
		return rrCreatedManually.isCreatedManually();
	}

	private boolean isManuallyCreatedResourceResolver(MethodInvocationTree methodInvocation) {
		return isRR(methodInvocation) && isRRF(methodInvocation);
	}

	private boolean isMethodInvocation(AssignmentExpressionTree tree) {
		return tree.expression().is(Kind.METHOD_INVOCATION);
	}

	private MethodTree getMethodTree(MethodInvocationTree methodInvocation) {
		IdentifierTree method = (IdentifierTree) methodInvocation.methodSelect();
		return (MethodTree) getDeclaration(method);
	}

	private Tree getDeclaration(IdentifierTree variable) {
		return variable.symbol().declaration();
	}

	@Override
	public void visitReturnStatement(ReturnStatementTree tree) {
		if (tree.expression() != null && tree.expression().is(Kind.IDENTIFIER)) {
			IdentifierTree identifier = (IdentifierTree) tree.expression();
			Tree declaration = identifier.symbol().declaration();
			if (resourceResolvers.contains(declaration)) {
				resourceResolvers.remove(declaration);
			}
		}
		super.visitReturnStatement(tree);
	}

	private boolean isRRF(MethodInvocationTree invocationTree) {
		return invocationTree.symbol().owner().type().fullyQualifiedName().equals(RESOURCE_RESOLVER_FACTORY);
	}

	private boolean isRR(MethodInvocationTree invocationTree) {
		return invocationTree.symbolType().fullyQualifiedName().equals(RESOURCE_RESOLVER_NAME);
	}

	@Override
	public void visitTryStatement(TryStatementTree tree) {
		// omit resources
		scan(tree.block());
		scan(tree.catches());
		scan(tree.finallyBlock());
	}

	private class CheckIfRRCreatedManually extends BaseTreeVisitor {

		private Tree declarationOfReturnedVariable;

		private boolean createdManually;

		@Override
		public void visitMethod(MethodTree tree) {
			FindDeclarationOfReturnedVariable visitor = new FindDeclarationOfReturnedVariable();
			tree.accept(visitor);
			declarationOfReturnedVariable = visitor.getDeclarationOfReturnedVariable();
			super.visitMethod(tree);
		}

		@Override
		public void visitAssignmentExpression(AssignmentExpressionTree tree) {
			if (isMethodInvocation(tree) && getDeclaration((IdentifierTree) tree.variable()).equals(declarationOfReturnedVariable)) {
				MethodInvocationTree methodInvocation = (MethodInvocationTree) tree.expression();
				if (isManuallyCreatedResourceResolver(methodInvocation)) {
					this.createdManually = true;
				} else {
					CheckIfRRCreatedManually rrCreatedManually = new CheckIfRRCreatedManually();
					getMethodTree(methodInvocation).accept(rrCreatedManually);
					this.createdManually = rrCreatedManually.isCreatedManually();
				}
			}
			super.visitAssignmentExpression(tree);
		}

		public boolean isCreatedManually() {
			return createdManually;
		}

	}

	private class FindDeclarationOfReturnedVariable extends BaseTreeVisitor {

		private Tree declarationOfReturnedVariable;

		@Override
		public void visitReturnStatement(ReturnStatementTree tree) {
			if (tree.expression() != null && tree.expression().is(Kind.IDENTIFIER)) {
				IdentifierTree identifier = (IdentifierTree) tree.expression();
				declarationOfReturnedVariable = getDeclaration(identifier);
			}
			super.visitReturnStatement(tree);
		}

		public Tree getDeclarationOfReturnedVariable() {
			return declarationOfReturnedVariable;
		}

	}
}