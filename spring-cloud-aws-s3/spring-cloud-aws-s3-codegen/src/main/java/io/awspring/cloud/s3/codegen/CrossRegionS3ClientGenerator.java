/*
 * Copyright 2013-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.awspring.cloud.s3.codegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Comparator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.resolution.MethodUsage;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ClassLoaderTypeSolver;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;

public final class CrossRegionS3ClientGenerator {

	private CrossRegionS3ClientGenerator() {

	}

	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			throw new RuntimeException("Need 1 parameter: the JavaParser source checkout root directory.");
		}

		final Path source = Paths.get(args[0], "..", "spring-cloud-aws-s3-codegen", "src", "main", "java", "io",
				"awspring", "cloud", "s3", "codegen", "CrossRegionS3ClientTemplate.java");
		CompilationUnit compilationUnit = StaticJavaParser.parse(source);
		compilationUnit.setPackageDeclaration("io.awspring.cloud.s3");
		ClassOrInterfaceDeclaration classOrInterfaceDeclaration = compilationUnit
				.getClassByName("CrossRegionS3ClientTemplate").get();
		classOrInterfaceDeclaration.setName("CrossRegionS3Client");
		addOverriddenMethods(classOrInterfaceDeclaration);
		classOrInterfaceDeclaration.getConstructors()
				.forEach(constructorDeclaration -> constructorDeclaration.setName("CrossRegionS3Client"));
		System.out.println(compilationUnit);

		// generate file
		final Path generatedJavaCcRoot = Paths.get(args[0], "..", "spring-cloud-aws-s3", "src", "main", "java", "io",
				"awspring", "cloud", "s3", "CrossRegionS3Client.java");
		Files.write(generatedJavaCcRoot, Collections.singletonList(compilationUnit.toString()));
	}

	private static void addOverriddenMethods(ClassOrInterfaceDeclaration crossRegionS3Client) {
		TypeSolver typeSolver = new ClassLoaderTypeSolver(CrossRegionS3ClientGenerator.class.getClassLoader());
		ResolvedReferenceTypeDeclaration resolvedReferenceTypeDeclaration = typeSolver
				.solveType(S3Client.class.getName());
		resolvedReferenceTypeDeclaration.getAllMethods().stream().sorted(Comparator.comparing(MethodUsage::getName))
				.forEach(u -> {
					if (!u.getName().equals("listBuckets") && u.getParamTypes().size() == 1
							&& u.getParamType(0).describe().endsWith("Request")) {
						MethodDeclaration methodDeclaration = crossRegionS3Client.addMethod(u.getName(),
								Modifier.Keyword.PUBLIC);
						methodDeclaration.addParameter(
								new Parameter(new ClassOrInterfaceType(u.getParamType(0).describe()), "request"));
						methodDeclaration
								.setType(new ClassOrInterfaceType(u.getDeclaration().getReturnType().describe()));
						methodDeclaration.setBody(new BlockStmt()
								.addStatement("return executeInBucketRegion(request.bucket(), s3Client -> s3Client."
										+ u.getName() + "(request));"));
						methodDeclaration.addMarkerAnnotation(Override.class);
						methodDeclaration.addThrownException(AwsServiceException.class);
						methodDeclaration.addThrownException(SdkClientException.class);
					}
				});
	}

}
