package it.maivisto.qualifiers;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.inject.Qualifier;

import org.grouplens.grapht.annotation.DefaultImplementation;

import it.maivisto.models.ItemContentMatrixModel;

@Documented
@Qualifier
@DefaultImplementation(ItemContentMatrixModel.class)
@Target({ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ItemContentSimilarityModel {
}