/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.refactoring.rename;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.beanProperties.BeanProperty;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.refactoring.RenameRefactoring;
import com.intellij.refactoring.openapi.impl.JavaRenameRefactoringImpl;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Avdeev
 */
public abstract class BeanPropertyRenameHandler implements RenameHandler {

  public boolean isAvailableOnDataContext(DataContext dataContext) {
    return false;
  }

  public boolean isRenaming(DataContext dataContext) {
    return getProperty(dataContext) != null;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    performInvoke(editor, dataContext);
  }

  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
    performInvoke(null, dataContext);
  }

  private void performInvoke(@Nullable Editor editor, DataContext dataContext) {
    final BeanProperty property = getProperty(dataContext);
    assert property != null;
    PsiNamedElement element = property.getPsiElement();

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      final String newName = PsiElementRenameHandler.DEFAULT_NAME.getData(dataContext);
      assert newName != null;
      doRename(property, newName, false, false);
      return;
    }

    if (PsiElementRenameHandler.canRename(element.getProject(), editor, element)) {
      RenameDialog2 d = RenameDialog2Kt.createRenameDialog2(element,
                                                            editor,
                                                            null);
      d.setPerformRename((newName, isPreview, callback) -> {
        doRename(property, newName, d.getSearchInComments().getValue(), isPreview);
        callback.invoke();
        return Unit.INSTANCE;
      });
      RenameDialog2Kt.show(d);
    }
  }

  public static void doRename(@NotNull final BeanProperty property, final String newName, final boolean searchInComments, boolean isPreview) {
    final PsiElement psiElement = property.getPsiElement();
    final RenameRefactoring rename = new JavaRenameRefactoringImpl(psiElement.getProject(), psiElement, newName, searchInComments, false);
    rename.setPreviewUsages(isPreview);

    final PsiMethod setter = property.getSetter();
    if (setter != null) {
      final String setterName = PropertyUtil.suggestSetterName(newName);
      rename.addElement(setter, setterName);

      final PsiParameter[] setterParameters = setter.getParameterList().getParameters();
      if (setterParameters.length == 1) {
        final JavaCodeStyleManager manager = JavaCodeStyleManager.getInstance(psiElement.getProject());
        final String suggestedParameterName = manager.propertyNameToVariableName(property.getName(), VariableKind.PARAMETER);
        if (suggestedParameterName.equals(setterParameters[0].getName())) {
          rename.addElement(setterParameters[0], manager.propertyNameToVariableName(newName, VariableKind.PARAMETER));
        }
      }
    }

    final PsiMethod getter = property.getGetter();
    if (getter != null) {
      final String getterName = PropertyUtil.suggestGetterName(newName, getter.getReturnType());
      rename.addElement(getter, getterName);
    }

    rename.run();
  }

  @Nullable
  protected abstract BeanProperty getProperty(DataContext context);
}
