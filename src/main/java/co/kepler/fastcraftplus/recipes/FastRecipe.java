package co.kepler.fastcraftplus.recipes;

import co.kepler.fastcraftplus.craftgui.GUIFastCraft;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.*;

/**
 * A recipe that will be used by the FastCraft+ user interface.
 */
public abstract class FastRecipe implements Comparable<FastRecipe> {

    /**
     * Get the un-cloned Recipe associated with this FastRecipe, or null if none exists.
     *
     * @return Returns the Recipe associated with this FastRecipe.
     */
    protected abstract Recipe getRecipeInternal();

    /**
     * Get the Recipe associated with this FastRecipe, or null if none exists.
     *
     * @return Returns the Recipe associated with this FastRecipe.
     */
    public final Recipe getRecipe() {
        return RecipeUtil.cloneRecipe(getRecipeInternal());
    }

    /**
     * Get the un-cloned matrix of items used in the crafting table to craft this recipe.
     *
     * @return Returns the matrix of items, or null if this recipe cannot be crafted in a crafting table.
     */
    protected abstract ItemStack[] getMatrixInternal();

    /**
     * Get the matrix of items used in the crafting table to craft this recipe.
     *
     * @return Returns the matrix of items, or null if this recipe cannot be crafted in a crafting table.
     */
    public final ItemStack[] getMatrix(int multiplier) {
        ItemStack[] result = getMatrixInternal();
        if (result == null) return null;
        result = result.clone();
        for (int i = 0; i < result.length; i++) {
            if (result[i] == null) continue;
            result[i] = result[i].clone();
            result[i].setAmount(result[i].getAmount() * multiplier);
        }
        return result;
    }

    /**
     * Get the un-cloned ingredients required to craft this recipe.
     *
     * @return Returns the ingredients required to craft this recipe.
     */
    protected abstract Map<Ingredient, Integer> getIngredientsInternal();

    /**
     * Get the ingredients required to craft this recipe.
     *
     * @return Returns the ingredients required to craft this recipe.
     */
    public final Map<Ingredient, Integer> getIngredients() {
        HashMap<Ingredient, Integer> result = new HashMap<>(getIngredientsInternal());
        result.remove(null);
        return result;
    }

    /**
     * Get the un-cloned results of this recipe.
     *
     * @return Returns the results of this recipe.
     */
    protected abstract List<ItemStack> getResultsInternal();

    /**
     * Get the results of this recipe.
     *
     * @return Returns the results of this recipe
     */
    public final List<ItemStack> getResults() {
        List<ItemStack> result = new ArrayList<>(getResultsInternal());
        for (int i = 0; i < result.size(); i++) {
            ItemStack item = result.get(i);
            if (item == null) continue;
            result.set(i, item.clone());
        }
        return result;
    }

    /**
     * Get the item shown in the FastCraft+ interface. Returns the first
     * result in getResults() by default.
     *
     * @return Returns the result shown in the FastCraft+ interface.
     */
    public ItemStack getDisplayResult() {
        List<ItemStack> results = getResults();
        return results.isEmpty() ? null : results.get(0).clone();
    }

    /**
     * Get the byproducts of this recipe. By default, returns one empty bucket
     * for every non-empty bucket used in the recipe.
     *
     * @return Returns the results of this recipe.
     */
    public List<ItemStack> getByproducts() {
        List<ItemStack> result = new ArrayList<>();

        // Count the number of buckets to be returned
        int buckets = 0;
        for (Ingredient i : getIngredients().keySet()) {
            switch (i.getMaterial()) {
            case LAVA_BUCKET:
            case MILK_BUCKET:
            case WATER_BUCKET:
                buckets += getIngredients().get(i);
            }
        }

        // Add buckets to the result
        int stackSize = Material.BUCKET.getMaxStackSize();
        while (buckets > stackSize) {
            result.add(new ItemStack(Material.BUCKET, stackSize));
            buckets -= stackSize;
        }
        if (buckets > 0) {
            result.add(new ItemStack(Material.BUCKET, buckets));
        }

        // Return the list of byproducts
        return result;
    }

    /**
     * Get this recipes results, including its main result, and its byproducts.
     *
     * @return Returns this recipe's results.
     */
    public final List<ItemStack> getAllResults() {
        List<ItemStack> items = new ArrayList<>();
        items.addAll(getResults());
        items.addAll(getByproducts());
        return items;
    }

    /**
     * Remove ingredients from an inventory.
     *
     * @param items The items to remove the ingredients from.
     * @return Returns true if the inventory had the necessary ingredients.
     */
    public boolean removeIngredients(ItemStack[] items, int multiplier) {
        // Clone the items in the player's inventory
        for (int i = 0; i < items.length; i++) {
            if (items[i] == null) continue;
            items[i] = items[i].clone();
            items[i].setAmount(items[i].getAmount());
        }

        // Add ingredients. Those that can use any data go at the end.
        LinkedList<Ingredient> toRemove = new LinkedList<>();
        Map<Ingredient, Integer> ingredients = getIngredients();
        for (Ingredient i : ingredients.keySet()) {
            if (i.anyData()) {
                toRemove.addLast(i);
            } else {
                toRemove.addFirst(i);
            }
        }

        // Remove ingredients.
        for (Ingredient i : toRemove) {
            if (!i.removeIngredients(items, ingredients.get(i) * multiplier)) {
                // If unable to remove all of this ingredient
                return false;
            }
        }

        return true;
    }

    /**
     * Craft this FastRecipe.
     *
     * @param gui The gui this recipe is being crafted in.
     * @return Returns true if the ingredients were removed from the player's inventory.
     */
    public List<ItemStack> craft(GUIFastCraft gui, int multiplier) {
        Player player = gui.getPlayer();
        ItemStack[] contents = player.getInventory().getContents();

        // Remove items, and return false if unable to craft
        if (!removeIngredients(contents, multiplier)) return null;

        // Attempt to craft in a crafting grid.
        ItemStack[] matrix = getMatrix(multiplier);
        Recipe recipe = getRecipe();
        if (matrix != null && recipe != null) {
            if (!RecipeUtil.callCraftItemEvent(player, recipe, matrix, getDisplayResult(), gui.getLocation())) {
                // If crafting cancelled
                return null;
            }
        }

        // Remove items from the player's inventory.
        player.getInventory().setContents(contents);

        // Award achievements
        for (ItemStack is : getResults()) {
            RecipeUtil.awardAchievement(player, is);
        }

        // Return this recipe's results
        List<ItemStack> results = new ArrayList<>();
        for (ItemStack is : getAllResults()) {
            ItemStack toAdd = is.clone();
            toAdd.setAmount(toAdd.getAmount() * multiplier);
            results.add(toAdd);
        }
        return results;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int compareTo(FastRecipe compareTo) {
        ItemStack result = getDisplayResult();
        ItemStack compResult = compareTo.getDisplayResult();

        // Compare item ID's
        int i = result.getTypeId() - compResult.getTypeId();
        if (i != 0) return i;

        // Compare item data
        i = result.getData().getData() - compResult.getData().getData();
        if (i != 0) return i;

        // Compare amounts
        return result.getAmount() - compResult.getAmount();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !(o instanceof FastRecipe)) return false;

        FastRecipe fr = (FastRecipe) o;
        if (!getResults().equals(fr.getResults())) return false;
        if (!getDisplayResult().equals(fr.getDisplayResult())) return false;
        if (!getIngredients().equals(fr.getIngredients())) return false;
        return getByproducts().equals(fr.getByproducts());
    }

    @Override
    public int hashCode() {
        int hash = getDisplayResult().hashCode();
        hash = hash * 31 + getIngredients().hashCode();
        hash = hash * 31 + getByproducts().hashCode();
        hash = hash * 31;
        for (ItemStack is : getResults()) {
            hash += is.hashCode();
        }
        return hash;
    }
}
