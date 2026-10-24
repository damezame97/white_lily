package com.damezame97.white_lily.gametest;

import com.damezame97.white_lily.recipebook.*;
import com.damezame97.white_lily.recipebook.browser_display.Display;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.gametest.*;

import java.util.ArrayList;
import java.util.List;

import static com.damezame97.white_lily.gametest.MedicalGameTests.yes;

/** Pure geometry/history/response tests for the browser interaction repair. */
@GameTestHolder("white_lily") @PrefixGameTestTemplate(false) @SuppressWarnings("unused")
public final class RecipeBrowserInteractionGameTests
{
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("white_lily",path);}
    private static Display display(GameTestHelper h,String path)
    {
        RecipeManager manager=h.getLevel().getRecipeManager();
        var recipe=manager.byKey(ResourceLocation.fromNamespaceAndPath("minecraft",path)).orElseThrow();
        return browser_recipe_display.build(recipe,h.getLevel().registryAccess());
    }

    @GameTest(template="empty") public static void compactCraftingCardsFitThreeAtNormalHeight(GameTestHelper h)
    {
        Display table=display(h,"crafting_table"),stick=display(h,"stick"),chest=display(h,"chest"),torch=display(h,"torch");
        List<Display> displays=List.of(table,stick,chest,torch);
        List<Integer> heights=displays.stream().map(d->browser_card_layout.cardHeight(d,1F)).toList();
        yes(h,heights.stream().allMatch(height->height<=82),"one crafting card is at most 82 px at 100%");
        var normal=browser_layout.pages(heights,339,2);
        yes(h,normal.get(0).end()>=3,"a 440 px detail body shows at least three complete crafting recipes");
        var shorter=browser_layout.pages(heights,251,2);
        yes(h,shorter.get(0).end()>=3,"a 360 px window still shows three complete compact cards");
        var tiny=browser_layout.pages(heights,211,2);
        yes(h,tiny.get(0).end()>=2&&tiny.get(0).end()<3,"a smaller body reduces capacity instead of overlapping cards");
        var flattened=normal.stream().flatMap(page->displays.subList(page.first(),page.end()).stream()).toList();
        yes(h,flattened.size()==displays.size()&&flattened.stream().map(d->d.recipe().getId()).distinct().count()==displays.size(),
                "each real recipe appears once; alternatives are not duplicated into extra cards");
        yes(h,browser_card_layout.plusX(table)==136&&browser_card_layout.plusY(table)==30,"the plus button sits to the right of the actual result slot");
        yes(h,browser_card_layout.diagramWidth(table)<=160,"crafting cards do not reserve a large right-hand explanation column");
        Display cooked=display(h,"charcoal");
        yes(h,browser_card_layout.diagramHeight(cooked)>=78,"cooking cards keep the fuel slot and time/experience metadata instead of clipping them");
        h.succeed();
    }

    @GameTest(template="empty") public static void scaleAndWindowChangesRelocateTheSameRecipe(GameTestHelper h)
    {
        List<Integer> full=new ArrayList<>(),small=new ArrayList<>();
        for(int i=0;i<8;i++){full.add(82);small.add(46);}
        var fullPages=browser_layout.pages(full,339,2);
        var smallPages=browser_layout.pages(small,339,2);
        yes(h,smallPages.get(0).end()>fullPages.get(0).end(),"50% actually increases per-page capacity");
        int target=6;
        int fullPage=browser_layout.locate(fullPages,target);
        int smallPage=browser_layout.locate(smallPages,target);
        yes(h,smallPages.get(smallPage).contains(target)&&fullPages.get(fullPage).contains(target),"the current recipe remains on a valid page after scale changes");
        yes(h,browser_card_layout.cardHeight(70,1F)==80&&browser_card_layout.cardHeight(70,.5F)==45,"card height follows the real scale");
        yes(h,browser_layout.itemRows(232,41,36,18)==8,"compressed header starts the item grid immediately under search and adds real rows");
        yes(h,browser_layout.itemRows(96,41,36,18)>=1,"a small panel never calculates zero rows");
        h.succeed();
    }

    @GameTest(template="empty") public static void foregroundMenusStayInsideTheScreenAndAboveTheAnchor(GameTestHelper h)
    {
        var bottom=browser_layout.placePopup(292,224,18,18,180,88,320,240,true);
        yes(h,bottom.x()>=4&&bottom.y()>=4&&bottom.x()+bottom.w()<=316&&bottom.y()+bottom.h()<=236,"bottom/right edge popup is clamped into the screen");
        yes(h,bottom.y()+bottom.h()<=224,"a popup near the bottom opens upward");
        var top=browser_layout.placePopup(6,4,18,18,180,88,320,240,false);
        yes(h,top.y()>=4&&top.y()+top.h()<=236,"a popup near the top stays readable");
        var oversized=browser_layout.placePopup(319,239,18,18,999,999,320,240,true);
        yes(h,oversized.x()>=4&&oversized.y()>=4&&oversized.x()+oversized.w()<=316&&oversized.y()+oversized.h()<=236,"oversized menu dimensions are clamped rather than drawn offscreen");
        yes(h,bottom.contains(300,180)&&!bottom.contains(2,2),"menu hit testing includes its surface and excludes the outside");
        h.succeed();
    }

    @GameTest(template="empty") public static void serverResponseMustMatchMenuRequestAndRecipe(GameTestHelper h)
    {
        ResourceLocation recipe=id("interaction/recipe");
        var state=new browser_transfer_state();
        yes(h,state.begin(11,7,recipe)&&state.isWaiting(),"one request starts a bounded wait");
        yes(h,!state.begin(12,7,recipe),"a second click cannot submit while waiting");
        yes(h,state.accept(new browser_transfer_feedback.Reply(7,recipe,10,transfer_reason.OK))==browser_transfer_state.Result.NONE,"an older request id is ignored");
        yes(h,state.accept(new browser_transfer_feedback.Reply(8,recipe,11,transfer_reason.OK))==browser_transfer_state.Result.NONE,"a different menu session is ignored");
        yes(h,state.accept(new browser_transfer_feedback.Reply(7,recipe,11,transfer_reason.MISSING_MATERIALS))==browser_transfer_state.Result.FAILURE
                &&state.reason()==transfer_reason.MISSING_MATERIALS,"a failed reply keeps the detail open with its real reason");
        yes(h,!state.isWaiting()&&state.accept(new browser_transfer_feedback.Reply(7,recipe,11,transfer_reason.OK))==browser_transfer_state.Result.NONE,"a duplicate reply cannot later close a new screen");
        yes(h,state.begin(13,7,recipe),"the player can retry after a failure");
        state.accept(new browser_transfer_feedback.Reply(7,recipe,13,transfer_reason.OK));
        yes(h,state.begin(14,7,recipe),"a new detail screen gets a new request state");
        yes(h,state.accept(new browser_transfer_feedback.Reply(7,recipe,13,transfer_reason.OK))==browser_transfer_state.Result.NONE,"a late success cannot close the new screen");
        state.cancel();
        yes(h,!state.isWaiting(),"closing the detail cancels the wait without mutating the server");
        h.succeed();
    }

    @GameTest(template="empty") public static void timeoutMatchesOnlyTheCurrentResponseAndFeedbackQueueIsCorrelated(GameTestHelper h)
    {
        ResourceLocation recipe=id("interaction/timeout");
        var state=new browser_transfer_state();
        state.begin(9001,12,recipe);
        for(int i=0;i<80;i++)yes(h,!state.tick(),"the wait remains active for its first 80 ticks");
        yes(h,state.tick()&&!state.isWaiting(),"an unanswered request expires instead of disabling the button forever");
        state.begin(9002,12,recipe);
        var reply=new browser_transfer_feedback.Reply(12,recipe,9002,transfer_reason.OK);
        browser_transfer_feedback.accept(reply);
        yes(h,browser_transfer_feedback.take(9002,12,recipe)!=null,"a reply is consumed only by the matching screen");
        yes(h,browser_transfer_feedback.take(9002,12,recipe)==null,"one reply is consumed at most once");
        h.succeed();
    }

    @GameTest(template="empty") public static void historySurvivesDetailClosureAndBackDoesNotRepush(GameTestHelper h)
    {
        var history=new browser_history();
        var jungle=new browser_history.Query(new net.minecraft.world.item.ItemStack(Items.JUNGLE_PLANKS),false,false,
                ResourceLocation.fromNamespaceAndPath("minecraft","crafting"),ResourceLocation.fromNamespaceAndPath("minecraft","jungle_planks"),0,"",0,false);
        var book=new browser_history.Query(new net.minecraft.world.item.ItemStack(Items.BOOK),false,false,
                ResourceLocation.fromNamespaceAndPath("minecraft","crafting"),ResourceLocation.fromNamespaceAndPath("minecraft","book"),0,"",0,false);
        // Close jungle detail, then open book detail in a new Screen.
        history.push(jungle,book.subject(),false,false);
        yes(h,history.size()==1,"closing a detail keeps its query in the session history");
        var restored=history.pop();
        yes(h,restored!=null&&restored.subject().is(Items.JUNGLE_PLANKS)&&restored.recipe().getPath().equals("jungle_planks"),"Back returns to the previous jungle planks query");
        yes(h,history.isEmpty(),"popping Back does not itself push the page just left");
        history.push(jungle,book.subject(),false,false);
        yes(h,history.size()==1,"reopening the book after Back can restore the same prior query again");
        history.push(jungle,jungle.subject(),false,false);
        yes(h,history.size()==1,"refreshing the same target/mode does not add another entry");
        history.push(jungle,book.subject(),true,false);
        yes(h,history.size()==2,"the same target in uses mode is a different query");
        history.clear();
        yes(h,history.isEmpty(),"leaving the world clears session history");
        h.succeed();
    }

    @GameTest(template="empty") public static void bottomControlStripReservesSpaceAndItemScaleChangesCapacity(GameTestHelper h)
    {
        for(int panelHeight:new int[]{140,180,232,400})
            for(int cell:new int[]{9,18,27})
            {
                int rows=browser_layout.itemRows(panelHeight,41,36,cell);
                int gridBottom=41+rows*cell;
                yes(h,rows>=1&&gridBottom<=panelHeight-36,"item grid never enters the reserved scale/cheat/page strip");
            }
        yes(h,browser_layout.itemRows(232,41,36,9)>browser_layout.itemRows(232,41,36,27),"shrinking the item list really adds rows");
        yes(h,browser_layout.cell(50)==9&&browser_layout.cell(100)==18&&browser_layout.cell(150)==27,"bottom scale reuses the existing 50-150% item pitch");
        yes(h,browser_layout.sidebarEntry(false)==browser_layout.SidebarEntry.REVEAL_SIDEBAR
                &&browser_layout.sidebarEntry(true)==browser_layout.SidebarEntry.OPEN_PAGE,
                "compact entry restores the sidebar when it fits and uses the overlay page only when space is short");
        for(int width:new int[]{68,100,130,200,400})
            for(int height:new int[]{95,120,180,232,400})
                for(int cell:new int[]{9,18,27})
                {
                    if(height<41+cell+36)continue;
                    int columns=browser_layout.columns(width,cell);
                    int rows=browser_layout.itemRows(height,41,36,cell);
                    yes(h,4+columns*cell<=width-4&&41+rows*cell<=height-36,"dynamic columns/rows stay inside the panel without fixed 7x7 capacity");
                }
        yes(h,browser_layout.columns(400,18)>browser_layout.columns(130,18)&&browser_layout.columns(400,18)!=7,"available logical width increases columns beyond the old fixed sidebar");
        yes(h,browser_layout.itemRows(400,41,36,18)>browser_layout.itemRows(232,41,36,18),"available logical height increases rows");
        yes(h,browser_layout.columns(200,9)>browser_layout.columns(200,27),"a smaller saved item scale adds columns");
        yes(h,browser_card_layout.cardHeight(70,1F)==80,"recipe-detail card height is independent of the item-list scale");
        h.succeed();
    }
}
