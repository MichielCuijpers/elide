/*
 * Copyright 2017, Yahoo Inc.
 * Licensed under the Apache License, Version 2.0
 * See LICENSE file in project root for terms.
 */
package com.yahoo.elide.core.filter;

import com.yahoo.elide.core.EntityDictionary;
import com.yahoo.elide.core.Path;
import com.yahoo.elide.core.RelationshipType;
import com.yahoo.elide.core.RequestScope;
import com.yahoo.elide.core.exceptions.InvalidOperatorNegationException;
import com.yahoo.elide.core.filter.expression.FilterExpression;
import com.yahoo.elide.core.filter.expression.Visitor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Predicate class.
 */
@EqualsAndHashCode
public class FilterPredicate implements FilterExpression, Function<RequestScope, Predicate> {
    @Getter @NonNull private Path path;
    @Getter @NonNull private Operator operator;
    @Getter @NonNull private List<Object> values;
    private static final String UNDERSCORE = "_";
    private static final String PERIOD = ".";

    public static boolean toManyInPath(EntityDictionary dictionary, Path path) {
        return path.getPathElements().stream()
                .map(element -> dictionary.getRelationshipType(element.getType(), element.getFieldName()))
                .anyMatch(RelationshipType::isToMany);
    }

    public FilterPredicate(Path.PathElement pathElement, Operator op, List<Object> values) {
        this(new Path(Arrays.asList(pathElement)), op, values);
    }

    public FilterPredicate(Path.PathElement pathElement, Operator op) {
        this(new Path(Arrays.asList(pathElement)), op, Collections.emptyList());
    }

    public FilterPredicate(Path path, Operator op) {
        this(path, op, Collections.emptyList());
    }

    public FilterPredicate(Path path, Operator op, List<Object> values) {
        this.path = path;
        this.operator = op;
        this.values = values;
    }

    public FilterPredicate(FilterPredicate copy) {
        this.path = new Path(new ArrayList<>(copy.getPath().getPathElements()));
        this.operator = copy.getOperator();
        this.values = new ArrayList<>(copy.getValues());
    }

    public String getField() {
        List<Path.PathElement> elements = path.getPathElements();
        Path.PathElement last = elements.get(elements.size() - 1);
        return last.getFieldName();
    }

    public String getFieldPath() {
        StringBuilder fieldPath = new StringBuilder();
        for (Path.PathElement pathElement : path.getPathElements()) {
            if (fieldPath.length() != 0) {
                fieldPath.append(PERIOD);
            }
            fieldPath.append(pathElement.getFieldName());
        }
        return fieldPath.toString();
    }

    /**
     * Get a unique name for this predicate to be used as a parameter name.
     * @return unique name
     */
    public String getParameterName() {
        return getFieldPath().replace(PERIOD, UNDERSCORE) + UNDERSCORE + Integer.toHexString(hashCode());
    }

    public List<Pair<String, Object>> getNamedParameters() {
        String baseName = getParameterName() + UNDERSCORE;
        return IntStream.range(0, values.size())
                .mapToObj(idx -> Pair.of(String.format("%s%d", baseName, idx), values.get(idx)))
                .collect(Collectors.toList());
    }

    /**
     * Returns an alias that uniquely identifies the last collection of entities in the path.
     * @return An alias for the path.
     */
    public String getAlias() {
        List<Path.PathElement> elements = path.getPathElements();

        Path.PathElement last = elements.get(elements.size() - 1);

        if (elements.size() == 1) {
            return getTypeAlias(last.getType());
        }

        Path.PathElement previous = elements.get(elements.size() - 2);

        return getTypeAlias(previous.getType()) + UNDERSCORE + previous.getFieldName();
    }

    /**
     * @param type The type to alias
     * @return type name alias that will likely not conflict with other types or with reserved keywords.
     */
    public static String getTypeAlias(Class<?> type) {
        return type.getCanonicalName().replace(PERIOD, UNDERSCORE);
    }

    public Class getEntityType() {
        List<Path.PathElement> elements = path.getPathElements();
        Path.PathElement first = elements.get(0);
        return first.getType();
    }

    @Override
    public <T> T accept(Visitor<T> visitor) {
        return visitor.visitPredicate(this);
    }

    @Override
    public Predicate apply(RequestScope dictionary) {
        return operator.contextualize(getFieldPath(), values, dictionary);
    }

    public String getStringValueEscaped(String specialCharacter, String escapeCharacter) {
        return getValues().get(0).toString().replace(specialCharacter, escapeCharacter + specialCharacter);
    }

    public boolean isMatchingOperator() {
        return operator == Operator.INFIX
                || operator == Operator.INFIX_CASE_INSENSITIVE
                || operator == Operator.PREFIX
                || operator == Operator.PREFIX_CASE_INSENSITIVE
                || operator == Operator.POSTFIX
                || operator == Operator.POSTFIX_CASE_INSENSITIVE;
    }

    @Override
    public String toString() {
        List<Path.PathElement> elements = path.getPathElements();
        StringBuilder formattedPath = new StringBuilder();
        if (!elements.isEmpty()) {
            formattedPath.append(StringUtils.uncapitalize(elements.get(0).getType().getSimpleName()));
        }

        for (Path.PathElement element : elements) {
            formattedPath.append(PERIOD).append(element.getFieldName());

        }
        return formattedPath.append(' ').append(operator).append(' ').append(values).toString();
    }

    public void negate() {
        Operator op = this.getOperator();
        switch (op) {
            case GE:
                this.operator = Operator.LT;
                break;
            case GT:
                this.operator = Operator.LE;
                break;
            case LE:
                this.operator = Operator.GT;
                break;
            case LT:
                this.operator = Operator.GE;
                break;
            case IN:
                this.operator = Operator.NOT;
                break;
            case NOT:
                this.operator = Operator.IN;
                break;
            case TRUE:
                this.operator = Operator.FALSE;
                break;
            case FALSE:
                this.operator = Operator.TRUE;
                break;
            case ISNULL:
                this.operator = Operator.NOTNULL;
                break;
            case NOTNULL:
                this.operator = Operator.ISNULL;
                break;
            default:
                throw new InvalidOperatorNegationException();
        }
    }
}
