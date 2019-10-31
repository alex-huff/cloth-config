package me.shedaniel.clothconfig2.gui.entries;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import me.shedaniel.clothconfig2.ClothConfigInitializer;
import me.shedaniel.clothconfig2.api.RunSixtyTimesEverySec;
import me.shedaniel.clothconfig2.api.ScissorsHandler;
import me.shedaniel.clothconfig2.gui.widget.DynamicEntryListWidget.SmoothScrollingSettings;
import me.shedaniel.clothconfig2.gui.widget.DynamicNewSmoothScrollingEntryListWidget.Interpolation;
import me.shedaniel.clothconfig2.gui.widget.DynamicNewSmoothScrollingEntryListWidget.Precision;
import me.shedaniel.math.api.Rectangle;
import me.shedaniel.math.compat.RenderHelper;
import me.shedaniel.math.impl.PointHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.AbstractParentElement;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.Window;
import net.minecraft.util.math.MathHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static me.shedaniel.clothconfig2.ClothConfigInitializer.getBounceBackMultiplier;

@SuppressWarnings("deprecation")
public class DropdownBoxEntry<T> extends TooltipListEntry<T> {
    
    protected ButtonWidget resetButton;
    protected SelectionElement<T> selectionElement;
    @Nonnull private Supplier<T> defaultValue;
    @Nullable private Consumer<T> saveConsumer;
    
    @Deprecated
    public DropdownBoxEntry(String fieldName,
            @Nonnull String resetButtonKey,
            @Nullable Supplier<Optional<String[]>> tooltipSupplier, boolean requiresRestart,
            @Nullable Supplier<T> defaultValue,
            @Nullable Consumer<T> saveConsumer,
            @Nullable Iterable<T> selections,
            @Nonnull SelectionTopCellElement<T> topRenderer, @Nonnull SelectionCellCreator<T> cellCreator) {
        super(I18n.translate(fieldName), tooltipSupplier, requiresRestart);
        this.defaultValue = defaultValue;
        this.saveConsumer = saveConsumer;
        this.resetButton = new ButtonWidget(0, 0, MinecraftClient.getInstance().textRenderer.getStringWidth(I18n.translate(resetButtonKey)) + 6, 20, I18n.translate(resetButtonKey), widget -> {
            selectionElement.topRenderer.setValue(defaultValue.get());
            getScreen().setEdited(true, isRequiresRestart());
        });
        this.selectionElement = new SelectionElement(this, new Rectangle(0, 0, 150, 20), new DefaultDropdownMenuElement(selections == null ? ImmutableList.of() : ImmutableList.copyOf(selections)), topRenderer, cellCreator);
    }
    
    @Override
    public void render(int index, int y, int x, int entryWidth, int entryHeight, int mouseX, int mouseY, boolean isSelected, float delta) {
        super.render(index, y, x, entryWidth, entryHeight, mouseX, mouseY, isSelected, delta);
        Window window = MinecraftClient.getInstance().window;
        this.resetButton.active = isEditable() && getDefaultValue().isPresent() && (!defaultValue.get().equals(getValue()) || getConfigError().isPresent());
        this.resetButton.y = y;
        this.selectionElement.active = isEditable();
        this.selectionElement.bounds.y = y;
        if (MinecraftClient.getInstance().textRenderer.isRightToLeft()) {
            MinecraftClient.getInstance().textRenderer.drawWithShadow(I18n.translate(getFieldName()), window.getScaledWidth() - x - MinecraftClient.getInstance().textRenderer.getStringWidth(I18n.translate(getFieldName())), y + 5, getPreferredTextColor());
            this.resetButton.x = x;
            this.selectionElement.bounds.x = x + resetButton.getWidth() + 1;
        } else {
            MinecraftClient.getInstance().textRenderer.drawWithShadow(I18n.translate(getFieldName()), x, y + 5, getPreferredTextColor());
            this.resetButton.x = x + entryWidth - resetButton.getWidth();
            this.selectionElement.bounds.x = x + entryWidth - 150 + 1;
        }
        this.selectionElement.bounds.width = 150 - resetButton.getWidth() - 4;
        resetButton.render(mouseX, mouseY, delta);
        selectionElement.render(mouseX, mouseY, delta);
    }
    
    @Nonnull
    public ImmutableList<T> getSelections() {
        return selectionElement.menu.getSelections();
    }
    
    @Override
    public T getValue() {
        return selectionElement.getValue();
    }
    
    @Override
    public Optional<T> getDefaultValue() {
        return defaultValue == null ? Optional.empty() : Optional.ofNullable(defaultValue.get());
    }
    
    @Override
    public void save() {
        if (saveConsumer != null)
            saveConsumer.accept(getValue());
    }
    
    @Override
    public List<? extends Element> children() {
        return Lists.newArrayList(selectionElement, resetButton);
    }
    
    @Override
    public Optional<String> getError() {
        return selectionElement.topRenderer.getError();
    }
    
    @Override
    public void lateRender(int mouseX, int mouseY, float delta) {
        selectionElement.lateRender(mouseX, mouseY, delta);
    }
    
    @Override
    public int getMorePossibleHeight() {
        return selectionElement.getMorePossibleHeight();
    }
    
    @Override
    public boolean mouseScrolled(double double_1, double double_2, double double_3) {
        return selectionElement.mouseScrolled(double_1, double_2, double_3);
    }
    
    public static class SelectionElement<R> extends AbstractParentElement implements Drawable {
        protected Rectangle bounds;
        protected boolean active;
        protected SelectionTopCellElement<R> topRenderer;
        protected DropdownBoxEntry<R> entry;
        protected DropdownMenuElement<R> menu;
        protected boolean dontReFocus = false;
        
        public SelectionElement(DropdownBoxEntry<R> entry, Rectangle bounds, DropdownMenuElement<R> menu, SelectionTopCellElement<R> topRenderer, SelectionCellCreator<R> cellCreator) {
            this.bounds = bounds;
            this.entry = entry;
            this.menu = Objects.requireNonNull(menu);
            this.menu.entry = entry;
            this.menu.cellCreator = Objects.requireNonNull(cellCreator);
            this.menu.initCells();
            this.topRenderer = Objects.requireNonNull(topRenderer);
            this.topRenderer.entry = entry;
        }
        
        @Override
        public void render(int mouseX, int mouseY, float delta) {
            fill(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, -6250336);
            fill(bounds.x + 1, bounds.y + 1, bounds.x + bounds.width - 1, bounds.y + bounds.height - 1, -16777216);
            topRenderer.render(mouseX, mouseY, bounds.x, bounds.y, bounds.width, bounds.height, delta);
            if (menu.isExpended())
                menu.render(mouseX, mouseY, bounds, delta);
        }
        
        @Override
        public boolean mouseScrolled(double double_1, double double_2, double double_3) {
            if (menu.isExpended())
                return menu.mouseScrolled(double_1, double_2, double_3);
            return false;
        }
        
        public void lateRender(int mouseX, int mouseY, float delta) {
            if (menu.isExpended())
                menu.lateRender(mouseX, mouseY, delta);
        }
        
        public int getMorePossibleHeight() {
            if (menu.isExpended())
                return menu.getHeight();
            return -1;
        }
        
        public R getValue() {
            return topRenderer.getValue();
        }
        
        @Override
        public List<? extends Element> children() {
            return Lists.newArrayList(topRenderer, menu);
        }
        
        @Override
        public boolean mouseClicked(double double_1, double double_2, int int_1) {
            dontReFocus = false;
            boolean b = super.mouseClicked(double_1, double_2, int_1);
            if (dontReFocus) {
                setFocused(null);
                dontReFocus = false;
            }
            return b;
        }
    }
    
    public static abstract class DropdownMenuElement<R> extends AbstractParentElement {
        @Deprecated @Nonnull private SelectionCellCreator<R> cellCreator;
        @Deprecated @Nonnull private DropdownBoxEntry<R> entry;
        
        @Nonnull
        public SelectionCellCreator<R> getCellCreator() {
            return cellCreator;
        }
        
        @Nonnull
        public final DropdownBoxEntry<R> getEntry() {
            return entry;
        }
        
        @Nonnull
        public abstract ImmutableList<R> getSelections();
        
        public abstract void initCells();
        
        public abstract void render(int mouseX, int mouseY, Rectangle rectangle, float delta);
        
        public abstract void lateRender(int mouseX, int mouseY, float delta);
        
        public abstract int getHeight();
        
        public final boolean isExpended() {
            return (getEntry().selectionElement.getFocused() == getEntry().selectionElement.topRenderer || getEntry().selectionElement.getFocused() == getEntry().selectionElement.menu) && getEntry().getFocused() == getEntry().selectionElement && getEntry().getParent().getFocused() == getEntry();
        }
        
        @Override
        public abstract List<SelectionCellElement<R>> children();
    }
    
    public static class DefaultDropdownMenuElement<R> extends DropdownMenuElement<R> {
        @Nonnull protected ImmutableList<R> selections;
        @Nonnull protected List<SelectionCellElement<R>> cells;
        @Nonnull protected List<SelectionCellElement<R>> currentElements;
        protected String lastSearchKeyword = "";
        protected Rectangle lastRectangle;
        protected boolean scrolling;
        protected double scroll, target;
        protected long start;
        protected long duration;
        protected RunSixtyTimesEverySec clamper = () -> {
            target = clamp(target);
            if (target < 0) {
                target = target * getBounceBackMultiplier();
            } else if (target > getMaxScrollPosition()) {
                target = (target - getMaxScrollPosition()) * getBounceBackMultiplier() + getMaxScrollPosition();
            } else
                unregisterClamper();
        };
        
        public DefaultDropdownMenuElement(@Nonnull ImmutableList<R> selections) {
            this.selections = selections;
            this.cells = Lists.newArrayList();
            this.currentElements = Lists.newArrayList();
        }
        
        protected void unregisterClamper() {
            clamper.unregisterTick();
        }
        
        public final double clamp(double v) {
            return MathHelper.clamp(v, -SmoothScrollingSettings.CLAMP_EXTENSION, getMaxScrollPosition() + SmoothScrollingSettings.CLAMP_EXTENSION);
        }
        
        public double getMaxScroll() {
            return getCellCreator().getCellHeight() * currentElements.size();
        }
        
        protected double getMaxScrollPosition() {
            return Math.max(0, this.getMaxScroll() - (getHeight()));
        }
        
        @Override
        @Nonnull
        public ImmutableList<R> getSelections() {
            return selections;
        }
        
        @Override
        public void initCells() {
            for(R selection : getSelections()) {
                cells.add(getCellCreator().create(selection));
            }
            for(SelectionCellElement<R> cell : cells) {
                cell.entry = getEntry();
            }
            search();
        }
        
        public void search() {
            currentElements.clear();
            String keyword = this.lastSearchKeyword.toLowerCase();
            for(SelectionCellElement<R> cell : cells) {
                String key = cell.getSearchKey();
                if (key == null || key.toLowerCase().contains(keyword))
                    currentElements.add(cell);
            }
            if (!keyword.isEmpty()) {
                Comparator<SelectionCellElement> c = Comparator.comparingDouble(i -> i.getSearchKey() == null ? Double.MAX_VALUE : similarity(i.getSearchKey(), keyword));
                currentElements.sort(c.reversed());
            }
            scrollTo(0, false);
        }
        
        protected int editDistance(String s1, String s2) {
            s1 = s1.toLowerCase();
            s2 = s2.toLowerCase();
            
            int[] costs = new int[s2.length() + 1];
            for(int i = 0; i <= s1.length(); i++) {
                int lastValue = i;
                for(int j = 0; j <= s2.length(); j++) {
                    if (i == 0)
                        costs[j] = j;
                    else {
                        if (j > 0) {
                            int newValue = costs[j - 1];
                            if (s1.charAt(i - 1) != s2.charAt(j - 1))
                                newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                            costs[j - 1] = lastValue;
                            lastValue = newValue;
                        }
                    }
                }
                if (i > 0)
                    costs[s2.length()] = lastValue;
            }
            return costs[s2.length()];
        }
        
        protected double similarity(String s1, String s2) {
            String longer = s1, shorter = s2;
            if (s1.length() < s2.length()) { // longer should always have greater length
                longer = s2;
                shorter = s1;
            }
            int longerLength = longer.length();
            if (longerLength == 0) {
                return 1.0; /* both strings are zero length */
            }
            return (longerLength - editDistance(longer, shorter)) / (double) longerLength;
        }
        
        @Override
        public void render(int mouseX, int mouseY, Rectangle rectangle, float delta) {
            if (!getEntry().selectionElement.topRenderer.getSearchTerm().equals(lastSearchKeyword)) {
                lastSearchKeyword = getEntry().selectionElement.topRenderer.getSearchTerm();
                search();
            }
            updatePosition(delta);
            lastRectangle = rectangle.clone();
            lastRectangle.translate(0, -1);
        }
        
        private void updatePosition(float delta) {
            target = clamp(target);
            if ((target < 0 || target > getMaxScrollPosition()) && !clamper.isRegistered())
                clamper.registerTick();
            if (!Precision.almostEquals(scroll, target, Precision.FLOAT_EPSILON))
                scroll = (float) Interpolation.expoEase(scroll, target, Math.min((System.currentTimeMillis() - start) / ((double) duration), 1));
            else
                scroll = target;
        }
        
        @Override
        public void lateRender(int mouseX, int mouseY, float delta) {
            int last10Height = getHeight();
            int cWidth = getCellCreator().getCellWidth();
            fill(lastRectangle.x, lastRectangle.y + lastRectangle.height, lastRectangle.x + cWidth, lastRectangle.y + lastRectangle.height + last10Height + 1, -6250336);
            fill(lastRectangle.x + 1, lastRectangle.y + lastRectangle.height + 1, lastRectangle.x + cWidth - 1, lastRectangle.y + lastRectangle.height + last10Height, -16777216);
            RenderHelper.pushMatrix();
            
            ScissorsHandler.INSTANCE.scissor(new Rectangle(lastRectangle.x, lastRectangle.y + lastRectangle.height + 1, cWidth - 6, last10Height - 1));
            double yy = lastRectangle.y + lastRectangle.height - scroll;
            for(SelectionCellElement<R> cell : currentElements) {
                if (yy + getCellCreator().getCellHeight() >= lastRectangle.y + lastRectangle.height && yy <= lastRectangle.y + lastRectangle.height + last10Height + 1)
                    cell.render(mouseX, mouseY, lastRectangle.x, (int) yy, getMaxScrollPosition() > 6 ? getCellCreator().getCellWidth() - 6 : getCellCreator().getCellWidth(), getCellCreator().getCellHeight(), delta);
                else
                    cell.dontRender(delta);
                yy += getCellCreator().getCellHeight();
            }
            ScissorsHandler.INSTANCE.removeLastScissor();
            
            if (currentElements.isEmpty()) {
                TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
                String s = I18n.translate("text.cloth-config.dropdown.value.unknown");
                textRenderer.drawWithShadow(s, lastRectangle.x + getCellCreator().getCellWidth() / 2 - textRenderer.getStringWidth(s) / 2, lastRectangle.y + lastRectangle.height + 3, -1);
            }
            
            if (getMaxScrollPosition() > 6) {
                RenderHelper.disableTexture();
                int scrollbarPositionMinX = lastRectangle.x + getCellCreator().getCellWidth() - 6;
                int scrollbarPositionMaxX = scrollbarPositionMinX + 6;
                int height = (int) (((last10Height) * (last10Height)) / this.getMaxScrollPosition());
                height = MathHelper.clamp(height, 32, last10Height - 8);
                height -= Math.min((scroll < 0 ? (int) -scroll : scroll > getMaxScrollPosition() ? (int) scroll - getMaxScrollPosition() : 0), height * .95);
                height = Math.max(10, height);
                int minY = (int) Math.min(Math.max((int) scroll * (last10Height - height) / getMaxScrollPosition() + (lastRectangle.y + lastRectangle.height + 1), (lastRectangle.y + lastRectangle.height + 1)), (lastRectangle.y + lastRectangle.height + 1 + last10Height) - height);
                
                int bottomc = new Rectangle(scrollbarPositionMinX, minY, scrollbarPositionMaxX - scrollbarPositionMinX, height).contains(PointHelper.fromMouse()) ? 168 : 128;
                int topc = new Rectangle(scrollbarPositionMinX, minY, scrollbarPositionMaxX - scrollbarPositionMinX, height).contains(PointHelper.fromMouse()) ? 222 : 172;
                
                Tessellator tessellator = Tessellator.getInstance();
                BufferBuilder buffer = tessellator.getBufferBuilder();
                
                // Bottom
                buffer.begin(7, VertexFormats.POSITION_UV_COLOR);
                buffer.vertex(scrollbarPositionMinX, minY + height, 0.0D).texture(0, 1).color(bottomc, bottomc, bottomc, 255).next();
                buffer.vertex(scrollbarPositionMaxX, minY + height, 0.0D).texture(1, 1).color(bottomc, bottomc, bottomc, 255).next();
                buffer.vertex(scrollbarPositionMaxX, minY, 0.0D).texture(1, 0).color(bottomc, bottomc, bottomc, 255).next();
                buffer.vertex(scrollbarPositionMinX, minY, 0.0D).texture(0, 0).color(bottomc, bottomc, bottomc, 255).next();
                tessellator.draw();
                
                // Top
                buffer.begin(7, VertexFormats.POSITION_UV_COLOR);
                buffer.vertex(scrollbarPositionMinX, (minY + height - 1), 0.0D).texture(0, 1).color(topc, topc, topc, 255).next();
                buffer.vertex((scrollbarPositionMaxX - 1), (minY + height - 1), 0.0D).texture(1, 1).color(topc, topc, topc, 255).next();
                buffer.vertex((scrollbarPositionMaxX - 1), minY, 0.0D).texture(1, 0).color(topc, topc, topc, 255).next();
                buffer.vertex(scrollbarPositionMinX, minY, 0.0D).texture(0, 0).color(topc, topc, topc, 255).next();
                tessellator.draw();
                RenderHelper.enableTexture();
            }
            RenderHelper.popMatrix();
        }
        
        @Override
        public int getHeight() {
            return Math.max(Math.min(getCellCreator().getDropBoxMaxHeight(), (int) getMaxScroll()), 14);
        }
        
        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return isExpended() && mouseX >= lastRectangle.x && mouseX <= lastRectangle.x + getCellCreator().getCellWidth() && mouseY >= lastRectangle.y + lastRectangle.height && mouseY <= lastRectangle.y + lastRectangle.height + getHeight() + 1;
        }
        
        @Override
        public boolean mouseDragged(double double_1, double double_2, int int_1, double double_3, double double_4) {
            if (!isExpended())
                return false;
            if (int_1 == 0 && this.scrolling) {
                if (double_2 < (double) lastRectangle.y + lastRectangle.height) {
                    scrollTo(0, false);
                } else if (double_2 > (double) lastRectangle.y + lastRectangle.height + getHeight()) {
                    scrollTo(getMaxScrollPosition(), false);
                } else {
                    double double_5 = (double) Math.max(1, this.getMaxScrollPosition());
                    int int_2 = getHeight();
                    int int_3 = MathHelper.clamp((int) ((float) (int_2 * int_2) / (float) this.getMaxScrollPosition()), 32, int_2 - 8);
                    double double_6 = Math.max(1.0D, double_5 / (double) (int_2 - int_3));
                    this.offset(double_4 * double_6, false);
                }
                target = MathHelper.clamp(target, 0, getMaxScrollPosition());
                return true;
            }
            return false;
        }
        
        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double double_3) {
            if (isMouseOver(mouseX, mouseY)) {
                offset(ClothConfigInitializer.getScrollStep() * -double_3, true);
                return true;
            }
            return false;
        }
        
        protected void updateScrollingState(double double_1, double double_2, int int_1) {
            this.scrolling = isExpended() && lastRectangle != null && int_1 == 0 && double_1 >= (double) lastRectangle.x + getCellCreator().getCellWidth() - 6 && double_1 < (double) (lastRectangle.x + getCellCreator().getCellWidth());
        }
        
        @Override
        public boolean mouseClicked(double double_1, double double_2, int int_1) {
            if (!isExpended())
                return false;
            updateScrollingState(double_1, double_2, int_1);
            return super.mouseClicked(double_1, double_2, int_1) || scrolling;
        }
        
        public void offset(double value, boolean animated) {
            scrollTo(target + value, animated);
        }
        
        public void scrollTo(double value, boolean animated) {
            scrollTo(value, animated, ClothConfigInitializer.getScrollDuration());
        }
        
        public void scrollTo(double value, boolean animated, long duration) {
            target = clamp(value);
            
            if (animated) {
                start = System.currentTimeMillis();
                this.duration = duration;
            } else
                scroll = target;
        }
        
        @Override
        public List<SelectionCellElement<R>> children() {
            return currentElements;
        }
    }
    
    public static abstract class SelectionCellCreator<R> {
        public abstract SelectionCellElement<R> create(R selection);
        
        public abstract int getCellHeight();
        
        public abstract int getDropBoxMaxHeight();
        
        public int getCellWidth() {
            return 132;
        }
    }
    
    public static class DefaultSelectionCellCreator<R> extends SelectionCellCreator<R> {
        protected Function<R, String> toStringFunction;
        
        public DefaultSelectionCellCreator(Function<R, String> toStringFunction) {
            this.toStringFunction = toStringFunction;
        }
        
        public DefaultSelectionCellCreator() {
            this(Object::toString);
        }
        
        @Override
        public SelectionCellElement<R> create(R selection) {
            return new DefaultSelectionCellElement<>(selection, toStringFunction);
        }
        
        @Override
        public int getCellHeight() {
            return 14;
        }
        
        @Override
        public int getDropBoxMaxHeight() {
            return getCellHeight() * 7;
        }
    }
    
    public static abstract class SelectionCellElement<R> extends AbstractParentElement {
        @Deprecated @Nonnull private DropdownBoxEntry<R> entry;
        
        @Nonnull
        public final DropdownBoxEntry<R> getEntry() {
            return entry;
        }
        
        public abstract void render(int mouseX, int mouseY, int x, int y, int width, int height, float delta);
        
        public abstract void dontRender(float delta);
        
        @Nullable
        public abstract String getSearchKey();
        
        @Nullable
        public abstract R getSelection();
    }
    
    public static class DefaultSelectionCellElement<R> extends SelectionCellElement<R> {
        protected R r;
        protected int x;
        protected int y;
        protected int width;
        protected int height;
        protected boolean rendering;
        protected Function<R, String> toStringFunction;
        
        public DefaultSelectionCellElement(R r, Function<R, String> toStringFunction) {
            this.r = r;
            this.toStringFunction = toStringFunction;
        }
        
        @Override
        public void render(int mouseX, int mouseY, int x, int y, int width, int height, float delta) {
            rendering = true;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            boolean b = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
            if (b)
                fill(x + 1, y + 1, x + width - 1, y + height - 1, -15132391);
            MinecraftClient.getInstance().textRenderer.drawWithShadow(toStringFunction.apply(r), x + 6, y + 3, b ? 16777215 : 8947848);
        }
        
        @Override
        public void dontRender(float delta) {
            rendering = false;
        }
        
        @Nullable
        @Override
        public String getSearchKey() {
            return toStringFunction.apply(r);
        }
        
        @Nullable
        @Override
        public R getSelection() {
            return r;
        }
        
        @Override
        public List<? extends Element> children() {
            return Collections.emptyList();
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int int_1) {
            boolean b = rendering && mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
            if (b) {
                getEntry().selectionElement.topRenderer.setValue(r);
                getEntry().selectionElement.setFocused(null);
                getEntry().selectionElement.dontReFocus = true;
                return true;
            }
            return false;
        }
    }
    
    public static abstract class SelectionTopCellElement<R> extends AbstractParentElement {
        @Deprecated private DropdownBoxEntry<R> entry;
        
        public abstract R getValue();
        
        public abstract void setValue(R value);
        
        public abstract String getSearchTerm();
        
        public abstract Optional<String> getError();
        
        public final Optional<String> getConfigError() {
            return entry.getConfigError();
        }
        
        public DropdownBoxEntry<R> getParent() {
            return entry;
        }
        
        public final boolean hasConfigError() {
            return getConfigError().isPresent();
        }
        
        public final int getPreferredTextColor() {
            return getConfigError().isPresent() ? 16733525 : 16777215;
        }
        
        public void selectFirstRecommendation() {
            List<SelectionCellElement<R>> children = getParent().selectionElement.menu.children();
            for(SelectionCellElement<R> child : children) {
                if (child.getSelection() != null) {
                    setValue(child.getSelection());
                    getParent().selectionElement.setFocused(null);
                    break;
                }
            }
        }
        
        public abstract void render(int mouseX, int mouseY, int x, int y, int width, int height, float delta);
    }
    
    public static class DefaultSelectionTopCellElement<R> extends SelectionTopCellElement<R> {
        protected TextFieldWidget textFieldWidget;
        protected Function<String, R> toObjectFunction;
        protected Function<R, String> toStringFunction;
        protected R value;
        
        public DefaultSelectionTopCellElement(R value, Function<String, R> toObjectFunction, Function<R, String> toStringFunction) {
            this.value = Objects.requireNonNull(value);
            this.toObjectFunction = Objects.requireNonNull(toObjectFunction);
            this.toStringFunction = Objects.requireNonNull(toStringFunction);
            textFieldWidget = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, 0, 0, 148, 18, "") {
                @Override
                public void render(int int_1, int int_2, float float_1) {
                    boolean f = isFocused();
                    setFocused(DefaultSelectionTopCellElement.this.getParent().getParent().getFocused() == DefaultSelectionTopCellElement.this.getParent() && DefaultSelectionTopCellElement.this.getParent().getFocused() == DefaultSelectionTopCellElement.this.getParent().selectionElement && DefaultSelectionTopCellElement.this.getParent().selectionElement.getFocused() == DefaultSelectionTopCellElement.this && DefaultSelectionTopCellElement.this.getFocused() == this);
                    super.render(int_1, int_2, float_1);
                    setFocused(f);
                }
                
                @Override
                public boolean keyPressed(int int_1, int int_2, int int_3) {
                    if (int_1 == 257 || int_1 == 335) {
                        DefaultSelectionTopCellElement.this.selectFirstRecommendation();
                        return true;
                    }
                    return super.keyPressed(int_1, int_2, int_3);
                }
            };
            textFieldWidget.setHasBorder(false);
            textFieldWidget.setMaxLength(999999);
            textFieldWidget.setText(toStringFunction.apply(value));
            textFieldWidget.setChangedListener(s -> {
                if (!toStringFunction.apply(value).equals(s))
                    getParent().getScreen().setEdited(true, getParent().isRequiresRestart());
            });
        }
        
        @Override
        public void render(int mouseX, int mouseY, int x, int y, int width, int height, float delta) {
            textFieldWidget.x = x + 4;
            textFieldWidget.y = y + 6;
            textFieldWidget.setWidth(width - 8);
            textFieldWidget.setIsEditable(getParent().isEditable());
            textFieldWidget.setEditableColor(getPreferredTextColor());
            textFieldWidget.render(mouseX, mouseY, delta);
        }
        
        @Override
        public R getValue() {
            if (hasConfigError())
                return value;
            return toObjectFunction.apply(textFieldWidget.getText());
        }
        
        @Override
        public void setValue(R value) {
            textFieldWidget.setText(toStringFunction.apply(value));
            textFieldWidget.method_1883(0);
        }
        
        @Override
        public String getSearchTerm() {
            return textFieldWidget.getText();
        }
        
        @Override
        public Optional<String> getError() {
            if (toObjectFunction.apply(textFieldWidget.getText()) != null)
                return Optional.empty();
            return Optional.of("Invalid Value!");
        }
        
        @Override
        public List<? extends Element> children() {
            return Collections.singletonList(textFieldWidget);
        }
    }
}
