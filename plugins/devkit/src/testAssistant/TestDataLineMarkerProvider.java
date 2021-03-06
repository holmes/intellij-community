/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.devkit.testAssistant;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.util.PsiUtil;

import java.util.Collections;

/**
 * @author yole
 */
public class TestDataLineMarkerProvider extends RunLineMarkerContributor {
  public static final String TEST_DATA_PATH_ANNOTATION_QUALIFIED_NAME = "com.intellij.testFramework.TestDataPath";
  public static final String CONTENT_ROOT_VARIABLE = "$CONTENT_ROOT";
  public static final String PROJECT_ROOT_VARIABLE = "$PROJECT_ROOT";

  public AnAction getAdditionalAction(@NotNull PsiElement e) {

    PsiElement element = e.getParent();
    if (!(e instanceof PsiIdentifier) ||
        !(element instanceof PsiMethod) &&
        !(element instanceof PsiClass)) {
      return null;
    }

    if (!PsiUtil.isPluginProject(element.getProject())) {
      return null;
    }

    final VirtualFile file = PsiUtilCore.getVirtualFile(element);
    if (file == null || !ProjectFileIndex.SERVICE.getInstance(element.getProject()).isInTestSourceContent(file)) {
      return null;
    }
    if (element instanceof PsiMethod) {
      return ActionManager.getInstance().getAction("TestData.Navigate");
    } else {
      final PsiClass psiClass = (PsiClass)element;
      final String basePath = getTestDataBasePath(psiClass);
      if (basePath != null) {
        return new GotoTestDataAction(basePath, psiClass.getProject(), AllIcons.Nodes.Folder);
      }
    }
    return null;
  }

  @Nullable
  public static String getTestDataBasePath(@Nullable PsiClass psiClass) {
    if (psiClass == null) return null;

    final PsiAnnotation annotation =
      AnnotationUtil.findAnnotationInHierarchy(psiClass, Collections.singleton(TEST_DATA_PATH_ANNOTATION_QUALIFIED_NAME));
    if (annotation != null) {
      final PsiAnnotationMemberValue value = annotation.findAttributeValue(PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME);
      if (value instanceof PsiExpression) {
        final Project project = value.getProject();
        final PsiConstantEvaluationHelper evaluationHelper = JavaPsiFacade.getInstance(project).getConstantEvaluationHelper();
        final Object constantValue = evaluationHelper.computeConstantExpression(value, false);
        if (constantValue instanceof String) {
          String path = (String) constantValue;
          if (path.contains(CONTENT_ROOT_VARIABLE)) {
            final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
            final VirtualFile file = psiClass.getContainingFile().getVirtualFile();
            if (file == null) {
              return null;
            }
            final VirtualFile contentRoot = fileIndex.getContentRootForFile(file);
            if (contentRoot == null) return null;
            path = path.replace(CONTENT_ROOT_VARIABLE, contentRoot.getPath());
          }
          if (path.contains(PROJECT_ROOT_VARIABLE)) {
            final VirtualFile baseDir = project.getBaseDir();
            if (baseDir == null) {
              return null;
            }
            path = path.replace(PROJECT_ROOT_VARIABLE, baseDir.getPath());
          }
          return path;
        }
      }
    }
    return null;
  }
}
