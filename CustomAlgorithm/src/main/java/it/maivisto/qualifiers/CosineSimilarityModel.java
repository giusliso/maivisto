package it.maivisto.qualifiers;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.inject.Qualifier;

import org.grouplens.grapht.annotation.DefaultImplementation;
import org.grouplens.lenskit.knn.item.model.SimilarityMatrixModel;

@Documented
@Qualifier
@DefaultImplementation(SimilarityMatrixModel.class)
@Target({ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface CosineSimilarityModel {
}