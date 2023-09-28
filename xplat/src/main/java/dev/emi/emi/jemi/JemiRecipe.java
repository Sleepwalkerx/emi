package dev.emi.emi.jemi;

import com.google.common.collect.Lists;
import dev.emi.emi.EmiUtil;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.emi.emi.jemi.impl.JemiIngredientAcceptor;
import dev.emi.emi.jemi.impl.JemiRecipeLayoutBuilder;
import dev.emi.emi.jemi.impl.JemiRecipeSlot;
import dev.emi.emi.jemi.impl.JemiRecipeSlotBuilder;
import dev.emi.emi.jemi.widget.JemiSlotWidget;
import dev.emi.emi.jemi.widget.JemiTankWidget;
import dev.emi.emi.runtime.EmiDrawContext;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.library.focus.FocusGroup;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public class JemiRecipe<T> implements EmiRecipe {
	public List<EmiIngredient> inputs = Lists.newArrayList();
	public List<EmiIngredient> catalysts = Lists.newArrayList();
	public List<EmiStack> outputs = Lists.newArrayList();
	public EmiRecipeCategory recipeCategory;
	public Identifier id;
	public IRecipeCategory<T> category;
	public T recipe;
	public JemiRecipeLayoutBuilder builder = new JemiRecipeLayoutBuilder();
	public boolean allowTree = true;

	public JemiRecipe(EmiRecipeCategory recipeCategory, IRecipeCategory<T> category, T recipe) {
		this.recipeCategory = recipeCategory;
		this.category = category;
		this.recipe = recipe;
		Identifier id = category.getRegistryName(recipe);
		if (id != null) {
			this.id = new Identifier("jei", "/" + EmiUtil.subId(id));
		}
		category.setRecipe(builder, recipe, JemiPlugin.runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup());
		for (JemiRecipeSlotBuilder jrsb : builder.slots) {
			jrsb.acceptor.coerceStacks(jrsb.tooltipCallback, jrsb.renderers);
		}
		for (JemiIngredientAcceptor acceptor : builder.ingredients) {
			EmiIngredient stack = acceptor.build();
			if (acceptor.role == RecipeIngredientRole.INPUT) {
				inputs.add(stack);
			} else if (acceptor.role == RecipeIngredientRole.CATALYST) {
				catalysts.add(stack);
			} else if (acceptor.role == RecipeIngredientRole.OUTPUT) {
				if (stack.getEmiStacks().size() > 1) {
					allowTree = false;
				}
				outputs.addAll(stack.getEmiStacks());
			}
		}
	}

	@Override
	public EmiRecipeCategory getCategory() {
		return recipeCategory;
	}

	@Override
	public @Nullable Identifier getId() {
		return id;
	}

	@Override
	public List<EmiIngredient> getInputs() {
		return inputs;
	}

	@Override
	public List<EmiIngredient> getCatalysts() {
		return catalysts;
	}

	@Override
	public List<EmiStack> getOutputs() {
		return outputs;
	}

	@Override
	public int getDisplayWidth() {
		return category.getWidth();
	}

	@Override
	public int getDisplayHeight() {
		return category.getHeight();
	}

	@Override
	public boolean supportsRecipeTree() {
		return allowTree && EmiRecipe.super.supportsRecipeTree();
	}

	@Override
	@SuppressWarnings("unchecked")
	public void addWidgets(WidgetHolder widgets) {
		Optional<IRecipeLayoutDrawable<T>> opt = JemiPlugin.runtime.getRecipeManager().createRecipeLayoutDrawable(category, recipe, FocusGroup.EMPTY);
		JemiRecipeLayoutBuilder builder = new JemiRecipeLayoutBuilder();
		category.setRecipe(builder, recipe, JemiPlugin.runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup());
		for (JemiRecipeSlotBuilder jrsb : builder.slots) {
			jrsb.acceptor.coerceStacks(jrsb.tooltipCallback, jrsb.renderers);
		}
		if (opt.isPresent()) {
			IRecipeLayoutDrawable<T> drawable = opt.get();
			widgets.addDrawable(0, 0, getDisplayWidth(), getDisplayHeight(), (raw, mouseX, mouseY, delta) -> {
				EmiDrawContext context = EmiDrawContext.wrap(raw);
				category.getBackground().draw(context.raw());
				category.draw(recipe, drawable.getRecipeSlotsView(), context.raw(), mouseX, mouseY);
				context.resetColor();
			}).tooltip((x, y) -> {
				return category.getTooltipStrings(recipe, drawable.getRecipeSlotsView(), x, y).stream().map(t -> TooltipComponent.of(t.asOrderedText())).toList();
			}).mouseClickedHandler(
				(mouseX, mouseY, button) -> category.handleInput(recipe, mouseX, mouseY, InputUtil.Type.MOUSE.createFromCode(button))
			).keyPressedHandler(
				(keyCode, scanCode, modifiers) -> {
					MinecraftClient client = MinecraftClient.getInstance();
					double mouseX = client.mouse.getX() * (double)client.getWindow().getScaledWidth() / (double)client.getWindow().getWidth();
					double mouseY = client.mouse.getY() * (double)client.getWindow().getScaledHeight() / (double)client.getWindow().getHeight();
					return category.handleInput(recipe, mouseX, mouseY, InputUtil.fromKeyCode(keyCode, scanCode));
				}
			);
			for (JemiRecipeSlotBuilder sb : builder.slots) {
				JemiRecipeSlot slot = new JemiRecipeSlot(sb);
				if (slot.tankInfo != null && !slot.getIngredients(JemiUtil.getFluidType()).toList().isEmpty()) {
					widgets.add(new JemiTankWidget(slot, this));
				} else {
					widgets.add(new JemiSlotWidget(slot, this));
				}
			}
		}
	}
}
