package com.banktaglayouts;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.Point;
import net.runelite.api.Varbits;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.ItemQuantityMode;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

import javax.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;

import static net.runelite.client.plugins.banktags.BankTagsPlugin.*;

@Slf4j
public class FakeItemOverlay extends Overlay {

	public static final int BANK_ITEM_WIDTH = 36;
	public static final int BANK_ITEM_HEIGHT = 32;
	public static final int BANK_ITEM_X_PADDING = 12;
	public static final int BANK_ITEM_Y_PADDING = 4;
	public static final int BANK_ITEMS_PER_ROW = 8;
	public static final int BANK_ITEM_START_X = 51;
	public static final int BANK_ITEM_START_Y = 0;

    @Inject
    private Client client;

    @Inject
    private ItemManager itemManager;

    @Inject
    private BankTagLayoutsPlugin plugin;

    @Inject
    private BankTagLayoutsConfig config;

    FakeItemOverlay()
    {
        drawAfterLayer(ComponentID.BANK_ITEM_CONTAINER);
        setLayer(OverlayLayer.MANUAL);
        setPosition(OverlayPosition.DYNAMIC);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        BankTagLayoutsPlugin.LayoutableThing currentLayoutableThing = plugin.getCurrentLayoutableThing();
        if (currentLayoutableThing == null) return null;

        Layout layout = plugin.getBankOrder(currentLayoutableThing);
        if (layout == null) return null;

        Widget bankItemContainer = client.getWidget(ComponentID.BANK_ITEM_CONTAINER);
        if (bankItemContainer == null) return null;
		int scrollY = bankItemContainer.getScrollY();
        Point canvasLocation = bankItemContainer.getCanvasLocation();

		int yOffset = 0;
		Widget widget = bankItemContainer;
        while (widget.getParent() != null) {
			yOffset += widget.getRelativeY();
        	widget = widget.getParent();
		}

		Rectangle bankItemArea = new Rectangle(canvasLocation.getX() + 51 - 6, yOffset, bankItemContainer.getWidth() - 51 + 6, bankItemContainer.getHeight());

        graphics.clip(bankItemArea);

		for (BankTagLayoutsPlugin.FakeItem fakeItem : plugin.fakeItems) {
			Widget c = bankItemContainer.getChild(fakeItem.index);
			if (fakeItem.isLayoutPlaceholder() && !config.showLayoutPlaceholders()) continue;

			int dragDeltaX = 0;
			int dragDeltaY = 0;
			if (fakeItem.index == plugin.draggedItemIndex && plugin.antiDrag.mayDrag()) {
				dragDeltaX = client.getMouseCanvasPosition().getX() - plugin.dragStartX;
				dragDeltaY = client.getMouseCanvasPosition().getY() - plugin.dragStartY;
				dragDeltaY += bankItemContainer.getScrollY() - plugin.dragStartScroll;
			}
			int fakeItemId = fakeItem.getItemId();

			int x = BankTagLayoutsPlugin.getXForIndex(fakeItem.index) + canvasLocation.getX() + dragDeltaX;
			int y = BankTagLayoutsPlugin.getYForIndex(fakeItem.index) + yOffset - scrollY + dragDeltaY;
			if (y + BankTagLayoutsPlugin.BANK_ITEM_HEIGHT > bankItemArea.getMinY() && y < bankItemArea.getMaxY())
			{
				if (fakeItem.isLayoutPlaceholder())
				{
					graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
					BufferedImage image = itemManager.getImage(fakeItemId, 1000, false);
					graphics.drawImage(image, x, y, image.getWidth(), image.getHeight(), null);
					BufferedImage outline = itemManager.getItemOutline(fakeItemId, 1000, Color.GRAY);
					graphics.drawImage(outline, x, y, null);
				} else {
					if (fakeItem.quantity == 0) {
						// placeholder.
						graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
					} else {
						graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
					}

					int quantityType = client.getVarbitValue(Varbits.BANK_QUANTITY_TYPE);
					int requestQty = client.getVarbitValue(Varbits.BANK_REQUESTEDQUANTITY);
					// ~script2759
					String suffix;
					switch (quantityType)
					{
						default:
							suffix = "1";
							break;
						case 1:
							suffix = "5";
							break;
						case 2:
							suffix = "10";
							break;
						case 3:
							suffix = Integer.toString(Math.max(1, requestQty));
							break;
						case 4:
							suffix = "All";
							break;
					}
					c.clearActions();

					c.setAction(0, "Withdraw-" + suffix);
					if (quantityType != 0)
					{
						c.setAction(1, "Withdraw-1");
					}
					c.setAction(2, "Withdraw-5");
					c.setAction(3, "Withdraw-10");
					if (requestQty > 0)
					{
						c.setAction(4, "Withdraw-" + requestQty);
					}
					c.setAction(5, "Withdraw-X");
					c.setAction(6, "Withdraw-All");
					c.setAction(7, "Withdraw-All-but-1");
					if (client.getVarbitValue(Varbits.BANK_LEAVEPLACEHOLDERS) == 0)
					{
						c.setAction(8, "Placeholder");
					}
					c.setAction(9, "Examine");

					int posX = (fakeItem.index % BANK_ITEMS_PER_ROW) * (BANK_ITEM_WIDTH + BANK_ITEM_X_PADDING) + BANK_ITEM_START_X;
					int posY = (fakeItem.index / BANK_ITEMS_PER_ROW) * (BANK_ITEM_HEIGHT + BANK_ITEM_Y_PADDING);

					ItemComposition def = client.getItemDefinition(fakeItemId);
					c.setItemId(fakeItemId);
					c.setItemQuantity(fakeItem.quantity);
					c.setItemQuantityMode(ItemQuantityMode.STACKABLE);
					c.setName("<col=ff9040>" + def.getName() + "</col>");
					c.setOriginalHeight(BankTagLayoutsPlugin.BANK_ITEM_HEIGHT);
					c.setOriginalWidth(BankTagLayoutsPlugin.BANK_ITEM_WIDTH);
					c.setOriginalX(posX);
					c.setOriginalY(posY);
					c.setHidden(false);
					c.revalidate();

					boolean showQuantity = itemManager.getItemComposition(fakeItemId).isStackable() || fakeItem.quantity != 1;
					/*BufferedImage image = itemManager.getImage(fakeItemId, fakeItem.quantity, showQuantity);
					graphics.drawImage(image, x, y, image.getWidth(), image.getHeight(), null);*/
				}
			}
		}

        return null;
    }
}
