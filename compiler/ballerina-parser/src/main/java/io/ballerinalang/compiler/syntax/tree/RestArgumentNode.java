/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package io.ballerinalang.compiler.syntax.tree;

import io.ballerinalang.compiler.internal.parser.tree.STNode;

import java.util.Objects;

/**
 * This is a generated syntax tree node.
 *
 * @since 2.0.0
 */
public class RestArgumentNode extends FunctionArgumentNode {

    public RestArgumentNode(STNode internalNode, int position, NonTerminalNode parent) {
        super(internalNode, position, parent);
    }

    public Token leadingComma() {
        return childInBucket(0);
    }

    public Token ellipsis() {
        return childInBucket(1);
    }

    public ExpressionNode expression() {
        return childInBucket(2);
    }

    @Override
    public void accept(NodeVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public <T> T apply(NodeTransformer<T> visitor) {
        return visitor.transform(this);
    }

    @Override
    protected String[] childNames() {
        return new String[]{
                "leadingComma",
                "ellipsis",
                "expression"};
    }

    public RestArgumentNode modify(
            Token leadingComma,
            Token ellipsis,
            ExpressionNode expression) {
        if (checkForReferenceEquality(
                leadingComma,
                ellipsis,
                expression)) {
            return this;
        }

        return NodeFactory.createRestArgumentNode(
                leadingComma,
                ellipsis,
                expression);
    }

    public RestArgumentNodeModifier modify() {
        return new RestArgumentNodeModifier(this);
    }

    /**
     * This is a generated tree node modifier utility.
     *
     * @since 2.0.0
     */
    public static class RestArgumentNodeModifier {
        private final RestArgumentNode oldNode;
        private Token leadingComma;
        private Token ellipsis;
        private ExpressionNode expression;

        public RestArgumentNodeModifier(RestArgumentNode oldNode) {
            this.oldNode = oldNode;
            this.leadingComma = oldNode.leadingComma();
            this.ellipsis = oldNode.ellipsis();
            this.expression = oldNode.expression();
        }

        public RestArgumentNodeModifier withLeadingComma(Token leadingComma) {
            Objects.requireNonNull(leadingComma, "leadingComma must not be null");
            this.leadingComma = leadingComma;
            return this;
        }

        public RestArgumentNodeModifier withEllipsis(Token ellipsis) {
            Objects.requireNonNull(ellipsis, "ellipsis must not be null");
            this.ellipsis = ellipsis;
            return this;
        }

        public RestArgumentNodeModifier withExpression(ExpressionNode expression) {
            Objects.requireNonNull(expression, "expression must not be null");
            this.expression = expression;
            return this;
        }

        public RestArgumentNode apply() {
            return oldNode.modify(
                    leadingComma,
                    ellipsis,
                    expression);
        }
    }
}