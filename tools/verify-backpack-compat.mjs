// 使用精妙背包实际库存类执行隔离回归，不构建模组、不启动游戏。
// 用法：node tools/verify-backpack-compat.mjs <精妙背包.jar> <JDK25目录>
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { existsSync, mkdtempSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { delimiter, dirname, join, resolve } from 'node:path'
import { homedir, tmpdir } from 'node:os'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../', import.meta.url))
const backpackJar = process.argv[2] ? resolve(process.argv[2]) : undefined
const javaHome = process.argv[3] || process.env.JAVA_HOME
assert.ok(backpackJar, '请提供精妙背包 jar 路径')
const bin = (name) => javaHome ? join(javaHome, 'bin', name) : name
const library = (group, artifact) => {
  const base = join(homedir(), '.gradle/caches/modules-2/files-2.1', group, artifact)
  for (const version of readdirSync(base).sort().reverse()) {
    for (const hash of readdirSync(join(base, version))) {
      const file = `${artifact}-${version}.jar`
      if (readdirSync(join(base, version, hash)).includes(file)) return join(base, version, hash, file)
    }
  }
  throw new Error(`找不到检查依赖 ${artifact}`)
}
const directory = mkdtempSync(join(tmpdir(), 'yzwc-backpack-'))
const output = join(directory, 'out'), actual = join(directory, 'actual'), native = join(directory, 'native')
for (const path of [output, actual, native]) mkdirSync(path)
const write = (file, content) => {
  const path = join(directory, 'src', file)
  mkdirSync(dirname(path), { recursive: true }); writeFileSync(path, content)
  return path
}
const pkg = 'top/csituka/youzaiworldcore'
const third = 'net/p3pp3rf1y/sophisticatedbackpacks'
const stubs = {
  'net/minecraft/world/Container.java': 'package net.minecraft.world; public interface Container {default int getContainerSize(){return 0;} default net.minecraft.world.item.ItemStack getItem(int slot){return net.minecraft.world.item.ItemStack.EMPTY;}}',
  'net/minecraft/world/level/ItemLike.java': 'package net.minecraft.world.level; public interface ItemLike {net.minecraft.world.item.Item asItem();}',
  'net/minecraft/world/item/Item.java': 'package net.minecraft.world.item; public class Item implements net.minecraft.world.level.ItemLike {public Item asItem(){return this;}}',
  'net/minecraft/world/item/ItemStack.java': `package net.minecraft.world.item;
    public class ItemStack { public static final ItemStack EMPTY = new ItemStack(new Item());
    public int ticks; public net.minecraft.world.entity.EquipmentSlot lastSlot;
    public void inventoryTick(net.minecraft.world.level.Level level, net.minecraft.world.entity.Entity entity, net.minecraft.world.entity.EquipmentSlot slot){ticks++;lastSlot=slot;}
    private final Item item; public ItemStack(Item item) { this.item=item; } public ItemStack(net.minecraft.world.level.ItemLike item){this(item.asItem());} public Item getItem() {return item;} public boolean isEmpty(){return this==EMPTY;} }`,
  'net/minecraft/world/entity/Entity.java': 'package net.minecraft.world.entity; public class Entity {}',
  'net/minecraft/world/entity/LivingEntity.java': 'package net.minecraft.world.entity; public class LivingEntity extends Entity {public net.minecraft.world.level.Level level(){return new net.minecraft.world.level.Level();}}',
  'net/minecraft/world/entity/EquipmentSlot.java': 'package net.minecraft.world.entity; public enum EquipmentSlot {CHEST,OFFHAND}',
  'net/minecraft/world/level/Level.java': 'package net.minecraft.world.level; public class Level {public long getGameTime(){return 0;}}',
  'net/minecraft/core/NonNullList.java': 'package net.minecraft.core; public class NonNullList<T> extends java.util.ArrayList<T> {}',
  'net/minecraft/world/entity/player/Inventory.java': `package net.minecraft.world.entity.player;
    public class Inventory {public net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> getNonEquipmentItems(){return new net.minecraft.core.NonNullList<>();}}`,
  'net/minecraft/world/entity/player/Player.java': `package net.minecraft.world.entity.player;
    public class Player extends net.minecraft.world.entity.LivingEntity {
    public eu.pb4.trinkets.api.TrinketAttachment trinkets;
    public net.minecraft.world.level.Level level(){return new net.minecraft.world.level.Level();}
    public net.minecraft.world.item.ItemStack getItemBySlot(net.minecraft.world.entity.EquipmentSlot slot){return net.minecraft.world.item.ItemStack.EMPTY;}
    public Inventory getInventory(){return new Inventory();} }`,
  'net/minecraft/world/inventory/Slot.java': `package net.minecraft.world.inventory;
    public class Slot {public net.minecraft.world.Container container; public Slot(net.minecraft.world.Container c){container=c;}}`,
  'net/minecraft/locale/Language.java': `package net.minecraft.locale; public class Language {
    public static Language getInstance(){return new Language();} public boolean has(String key){return false;} }`,
  'net/minecraft/network/chat/Component.java': `package net.minecraft.network.chat; public class Component {
    public static Component translatable(String key){return new Component();} public static Component literal(String key){return new Component();} }`,
  'net/minecraft/resources/Identifier.java': `package net.minecraft.resources; public class Identifier {
    public static Identifier tryParse(String key){return key.contains(":")?new Identifier():null;} }`,
  'net/fabricmc/api/EnvType.java': 'package net.fabricmc.api; public enum EnvType {CLIENT,SERVER}',
  'net/fabricmc/loader/api/FabricLoader.java': `package net.fabricmc.loader.api; public interface FabricLoader {
    public static FabricLoader getInstance(){return () -> net.fabricmc.api.EnvType.CLIENT;}
    net.fabricmc.api.EnvType getEnvironmentType(); }`,
  'eu/pb4/trinkets/api/SlotType.java': `package eu.pb4.trinkets.api; public interface SlotType {String getId();
    default boolean validatorCheck(net.minecraft.world.item.ItemStack stack,TrinketSlotAccess access,net.minecraft.world.entity.LivingEntity entity){return true;}}`,
  'eu/pb4/trinkets/api/TrinketSlotAccess.java': `package eu.pb4.trinkets.api;
    public record TrinketSlotAccess(net.minecraft.world.item.ItemStack get, boolean active) { public boolean canApplyEffects(){return active;} }`,
  'eu/pb4/trinkets/api/TrinketInventory.java': `package eu.pb4.trinkets.api;
    public interface TrinketInventory extends net.minecraft.world.Container {int getContainerSize(); boolean isVisible(int slot); TrinketSlotAccess getSlotAccess(int slot); SlotType slotType(); boolean hasCosmeticItems(); net.minecraft.world.item.ItemStack getCosmeticItem(int slot);
    default net.minecraft.world.item.ItemStack getItem(int slot){var access=getSlotAccess(slot);return access==null?net.minecraft.world.item.ItemStack.EMPTY:access.get();}}`,
  'eu/pb4/trinkets/impl/slots/TrinketSlot.java': 'package eu.pb4.trinkets.impl.slots; public interface TrinketSlot {boolean isDecorativeMode();}',
  'eu/pb4/trinkets/api/TrinketAttachment.java': `package eu.pb4.trinkets.api;
    public interface TrinketAttachment {java.util.Map<String,TrinketInventory> getInventories();default TrinketInventory getInventory(String id){return getInventories().get(id);}}`,
  'eu/pb4/trinkets/api/callback/TrinketCallback.java': `package eu.pb4.trinkets.api.callback;
    public interface TrinketCallback {java.util.Map<net.minecraft.world.item.Item,TrinketCallback> CALLBACKS=new java.util.HashMap<>();
    static void setCallback(net.minecraft.world.item.Item item,TrinketCallback callback){CALLBACKS.put(item,callback);}
    default void tick(net.minecraft.world.item.ItemStack stack,eu.pb4.trinkets.api.TrinketSlotAccess access,net.minecraft.world.entity.LivingEntity entity){}}`,
  'eu/pb4/trinkets/api/TrinketsApi.java': `package eu.pb4.trinkets.api; public class TrinketsApi {
    public static TrinketAttachment getAttachment(net.minecraft.world.entity.LivingEntity player) {return ((net.minecraft.world.entity.player.Player)player).trinkets;} }`,
  [`${pkg}/util/DebugLogger.java`]: `package top.csituka.youzaiworldcore.util; public final class DebugLogger { public static final int LEVEL_OFF=0,LEVEL_BASIC=1,LEVEL_DETAILED=2,LEVEL_DEBUG=3; public static final DebugLogger SERVER=new DebugLogger(),CLIENT=new DebugLogger(); private boolean developer; private int level; public void configure(boolean d,int l){developer=d;level=l;} public boolean isDevModeEnabled(){return developer;} public int getLogLevel(){return level;} public static boolean isEnabled(int l){return false;} public static boolean isEnabled(){return false;} public static DebugLogger bootstrap(){return CLIENT;} public static DebugLogger forFile(java.nio.file.Path p){return SERVER;} public ModuleLogger module(String m){return new ModuleLogger();} public static void notice(String m,String s,Object...a){} public static void warn(String m,String s,Object...a){} public static void error(String m,String s,Object...a){} public static void exception(String m,String s,Object...a){} public static void exceptionSummary(String m,String s,Object...a){} public static void info(String m,String s,Object...a){} public static void stateChange(String m,String s,Object...a){} public static void debug(String m,String s,Object...a){} public static void branch(String m,String s,Object...a){} public static void debugStateChange(String m,String s,Object...a){} public static void trace(String m,String s,Object...a){} public static void entering(String m,String s,Object...a){} public static void exiting(String m,String s,Object...a){} public static void traceBranch(String m,String s,Object...a){} public static void traceStateChange(String m,String s,Object...a){} public static final class ModuleLogger { public void notice(String s,Object...a){} public void info(String s,Object...a){} public void debug(String s,Object...a){} public void trace(String s,Object...a){} public void warn(String s,Object...a){} public void error(String s,Object...a){} public boolean isInfoEnabled(){return false;} public boolean isDebugEnabled(){return false;} public boolean isTraceEnabled(){return false;} } }`,
  [`${third}/backpack/BackpackItem.java`]: 'package net.p3pp3rf1y.sophisticatedbackpacks.backpack; public class BackpackItem extends net.minecraft.world.item.Item {}',
  [`${third}/init/ModItems.java`]: `package net.p3pp3rf1y.sophisticatedbackpacks.init;
    public class ModItems {private static final net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem ITEM=new net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem();
    public static final java.util.function.Supplier<net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem> BACKPACK=()->ITEM;
    public static final java.util.List<java.util.function.Supplier<net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem>> BACKPACKS=java.util.List.of(BACKPACK);}`,
  'net/p3pp3rf1y/sophisticatedcore/compat/ICompat.java': 'package net.p3pp3rf1y.sophisticatedcore.compat; public interface ICompat {default void init(){} default void setup(){}}',
}

try {
  const classes = ['util/PlayerInventoryProvider.class', 'util/PlayerInventoryProvider$RenderedBackpack.class',
    'util/PlayerInventoryProvider$RenderInfo.class', 'util/PlayerInventoryProvider$BackpackInventorySlotConsumer.class',
    'util/PlayerInventoryHandler.class', 'util/PlayerInventoryHandler$SlotCountGetter.class', 'util/PlayerInventoryHandler$SlotStackGetter.class',
    'client/KeybindHandler$PlayerInventoryReturn.class', 'compat/CompatModIds.class']
  execFileSync(bin('jar'), ['xf', backpackJar, ...classes.map((name) => `${third}/${name}`)], { cwd: actual })
  execFileSync(bin('jar'), ['xf', backpackJar, `${third}/compat/trinkets/TrinketsCompat.class`, `${third}/compat/trinkets/BackpackTrinket.class`], { cwd: native })
  const nativePresent = existsSync(join(native, third, 'compat/trinkets/TrinketsCompat.class'))
  const libraries = [library('net.fabricmc', 'sponge-mixin'), library('io.github.llamalad7', 'mixinextras-fabric'),
    library('org.ow2.asm', 'asm'), library('org.ow2.asm', 'asm-tree'), library('com.google.guava', 'guava')]
  const harness = write('BackpackRegression.java', `
import java.lang.reflect.*;
import java.util.*;
import eu.pb4.trinkets.api.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.*;
import net.p3pp3rf1y.sophisticatedbackpacks.util.*;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;
import top.csituka.youzaiworldcore.compat.SophisticatedBackpacksCompat;
import top.csituka.youzaiworldcore.client.inventory.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public class BackpackRegression {
    static int checks;
    static void check(boolean value,String name){if(!value)throw new AssertionError(name); checks++;}
    static class Inventory implements TrinketInventory {
        ItemStack[] items,cosmetics; boolean[] visible,active;
        Inventory(ItemStack...items){this.items=items; cosmetics=new ItemStack[items.length]; Arrays.fill(cosmetics,ItemStack.EMPTY); visible=new boolean[items.length]; active=new boolean[items.length]; Arrays.fill(visible,true); Arrays.fill(active,true);}
        public int getContainerSize(){return items.length;}
        public boolean isVisible(int slot){return visible[slot];}
        public TrinketSlotAccess getSlotAccess(int slot){return items[slot]==null?null:new TrinketSlotAccess(items[slot],active[slot]);}
        public SlotType slotType(){return ()->"chest/backpack";}
        public boolean hasCosmeticItems(){return true;}
        public ItemStack getCosmeticItem(int slot){return cosmetics[slot];}
    }
    public static void main(String[] args)throws Exception {
        var provider=PlayerInventoryProvider.get();
        SophisticatedBackpacksCompat.registerProvider(provider);
        if(args.length>0){check(provider.getPlayerInventoryHandler("trinkets").isEmpty(),"原生适配存在时不重复注册"); System.out.println("通过 "+checks+" 项原生兼容保留检查。"); return;}
        var handler=provider.getPlayerInventoryHandler("trinkets").orElseThrow();
        check(!handler.hasItsOwnRenderer(),"使用背包原有渲染器");
        var player=new Player();
        check(handler.getIdentifiers(player,0).isEmpty(),"缺少饰品附件时安全返回");
        check(handler.getSlotCount(player,"chest/backpack")==0,"缺少库存时计数为零");
        check(handler.getStackInSlot(player,"missing",0)==ItemStack.EMPTY,"缺少库存时返回空物品");
        var one=new ItemStack(new BackpackItem()); var two=new ItemStack(new BackpackItem());
        var inv=new Inventory(one,two,null);
        var inventories=new LinkedHashMap<String,TrinketInventory>(); inventories.put("chest/backpack",inv);
        player.trinkets=()->inventories;
        check(handler.getIdentifiers(player,0).equals(Set.of("chest/backpack")),"实际饰品库存标识");
        check(handler.getSlotCount(player,"chest/backpack")==3,"多槽位计数");
        check(handler.getStackInSlot(player,"chest/backpack",1)==two,"读取实际物品而非副本");
        check(handler.getStackInSlot(player,"chest/backpack",-1)==ItemStack.EMPTY,"负编号安全返回");
        check(handler.getStackInSlot(player,"chest/backpack",3)==ItemStack.EMPTY,"越界安全返回");
        check(handler.getStackInSlot(player,"chest/backpack",2)==ItemStack.EMPTY,"空槽访问安全返回");
        inv.visible[0]=false;
        check(handler.getStackInSlot(player,"chest/backpack",0)==one,"隐藏背包仍可用于功能处理");
        check(SophisticatedBackpacksCompat.withRenderVisibility(()->handler.getStackInSlot(player,"chest/backpack",0))==ItemStack.EMPTY,"渲染时隐藏饰品");
        check(SophisticatedBackpacksCompat.withRenderVisibility(()->provider.getBackpackFromRendered(player)).orElseThrow().backpack()==two,"实际渲染查询选择可见背包");
        try {SophisticatedBackpacksCompat.withRenderVisibility(()->{throw new IllegalStateException();});} catch(IllegalStateException expected){}
        check(handler.getStackInSlot(player,"chest/backpack",0)==one,"异常后恢复功能查询");
        check(SophisticatedBackpacksCompat.withRenderVisibility(()->{
            SophisticatedBackpacksCompat.withRenderVisibility(()->false);
            return handler.getStackInSlot(player,"chest/backpack",0)==ItemStack.EMPTY;
        }),"嵌套渲染查询保留外层可见性");
        var found=new ArrayList<ItemStack>();
        provider.runOnBackpacks(player,(stack,name,id,index)->{if(name.equals("trinkets"))found.add(stack); return false;});
        check(found.equals(List.of(one,two)),"实际背包枚举能找到饰品中的两只背包");
        inv.active[0]=false;
        check(handler.getStackInSlot(player,"chest/backpack",0)==ItemStack.EMPTY,"禁用效果的展示槽不参与背包升级");
        inv.visible[0]=true; inv.cosmetics[0]=two;
        check(SophisticatedBackpacksCompat.withRenderVisibility(()->provider.getBackpackFromRendered(player)).orElseThrow().backpack()==two,"外观背包覆盖同槽有效背包的渲染");
        check(handler.getStackInSlot(player,"chest/backpack",0)==ItemStack.EMPTY,"外观覆盖不会赋予展示槽升级能力");
        inv.active[0]=true;
        check(handler.getStackInSlot(player,"chest/backpack",0)==one,"外观覆盖不改变有效背包的升级来源");
        inv.visible[0]=false;
        check(SophisticatedBackpacksCompat.withRenderVisibility(()->handler.getStackInSlot(player,"chest/backpack",0))==ItemStack.EMPTY,"隐藏同时作用于外观背包");
        inv.cosmetics[0]=ItemStack.EMPTY;
        var other=new Player(); check(handler.getIdentifiers(other,0).isEmpty(),"玩家库存相互独立");
        Set<String> snapshot=handler.getIdentifiers(player,0); inventories.put("chest/elytra",new Inventory());
        check(!snapshot.contains("chest/elytra") && handler.getIdentifiers(player,0).contains("chest/elytra"),"标识实时刷新且旧快照不变");
        Field server=PlayerInventoryProvider.class.getDeclaredField("serverProvider"); server.setAccessible(true);
        Object serverProvider=server.get(null); SophisticatedBackpacksCompat.registerProvider(serverProvider);
        check(((PlayerInventoryProvider)serverProvider).getPlayerInventoryHandler("trinkets").isPresent(),"另一个侧的提供器也可注册");

        Class<?> mixin=Class.forName("top.csituka.youzaiworldcore.mixin.client.compat.SophisticatedBackpackKeybindMixin");
        Method recognize=mixin.getDeclaredMethod("youzaiworldcore$recognizeTrinket",net.minecraft.world.Container.class,CallbackInfoReturnable.class); recognize.setAccessible(true);
        var isTrinket=new CallbackInfoReturnable<Boolean>("test",true,false); recognize.invoke(null,inv,isTrinket);
        check(isTrinket.isCancelled()&&isTrinket.getReturnValue(),"快捷键识别饰品库存");
        Method resolve=mixin.getDeclaredMethod("youzaiworldcore$trinketInventory",Slot.class,CallbackInfoReturnable.class); resolve.setAccessible(true);
        var result=new CallbackInfoReturnable<Optional<?>>("test",true,Optional.empty()); resolve.invoke(null,new Slot(inv),result);
        Object value=result.getReturnValue().orElseThrow();
        Method id=value.getClass().getDeclaredMethod("identifier"), name=value.getClass().getDeclaredMethod("handlerName"); id.setAccessible(true); name.setAccessible(true);
        check(id.invoke(value).equals("chest/backpack")&&name.invoke(value).equals("trinkets"),"实际快捷键返回对象使用饰品标识");
        var vanilla=new CallbackInfoReturnable<Optional<?>>("test",true,Optional.empty()); resolve.invoke(null,new Slot(new net.minecraft.world.Container(){}),vanilla);
        check(!vanilla.isCancelled(),"不改变原版槽位快捷键");
        class CosmeticSlot extends Slot implements eu.pb4.trinkets.impl.slots.TrinketSlot {
            CosmeticSlot(){super(inv);} public boolean isDecorativeMode(){return true;}
        }
        var cosmeticResult=new CallbackInfoReturnable<Optional<?>>("test",true,Optional.empty());
        resolve.invoke(null,new CosmeticSlot(),cosmeticResult);
        check(cosmeticResult.isCancelled()&&cosmeticResult.getReturnValue().isEmpty(),"外观槽快捷键不能打开同编号有效背包");

        var rules=InventoryItemGroupDefaults.getBuiltInRules("sophisticatedbackpacks:main");
        check(rules.size()==4,"原标签页仍有四组");
        for(String idValue:List.of("backpack","copper_backpack","iron_backpack","gold_backpack","diamond_backpack","netherite_backpack")){
            check(rules.stream().anyMatch(r->r.matches("sophisticatedbackpacks:"+idValue)),"覆盖背包 "+idValue);
        }
        for(int i=1;i<=3;i++) check(rules.get(3).matches("sophisticatedbackpacks:stack_downgrade_tier_"+i),"降级物品分组");
        check(rules.get(3).matches("sophisticatedbackpacks:stack_upgrade_tier_1_conversion"),"保留旧版转换物品");
        check(!rules.get(2).matches("sophisticatedbackpacks:stack_upgrade_tier_1_conversion"),"转换物品不混入堆叠升级");
        check(InventoryItemGroupDefaults.getBuiltInRules("minecraft:search").isEmpty(),"不改变搜索标签页");
        System.out.println("通过 "+checks+" 项实际背包接口／饰品／分组检查。");
    }
}
`)
  const absent = write('BackpackAbsent.java', `public class BackpackAbsent {
    public static void main(String[]args)throws Exception {
        Class.forName("top.csituka.youzaiworldcore.compat.SophisticatedBackpacksCompat");
        Class.forName("top.csituka.youzaiworldcore.mixin.compat.SophisticatedBackpackInventoryMixin");
        Class.forName("top.csituka.youzaiworldcore.mixin.compat.SophisticatedBackpackTrinketsMixin");
        Class.forName("top.csituka.youzaiworldcore.mixin.compat.SophisticatedBackpackTrinketTickMixin");
        Class.forName("top.csituka.youzaiworldcore.mixin.client.compat.SophisticatedBackpackKeybindMixin");
        System.out.println("未安装精妙背包时五个适配类均可加载。");
    }} `)
  const source = Object.entries(stubs).map(([file, text]) => write(file, text))
  const production = [`src/main/java/${pkg}/compat/SophisticatedBackpacksCompat.java`,
    `src/main/java/${pkg}/mixin/compat/SophisticatedBackpackInventoryMixin.java`,
    `src/main/java/${pkg}/mixin/compat/SophisticatedBackpackTrinketsMixin.java`,
    `src/main/java/${pkg}/mixin/compat/SophisticatedBackpackTrinketTickMixin.java`,
    `src/client/java/${pkg}/mixin/client/compat/SophisticatedBackpackKeybindMixin.java`,
    `src/client/java/${pkg}/client/inventory/InventoryItemGroupDefaults.java`,
    `src/client/java/${pkg}/client/inventory/InventoryItemGroupRule.java`].map((path) => join(root, path))
  execFileSync(bin('javac'), ['-proc:none', '-encoding', 'UTF-8', '--release', '25', '-cp', [actual,...libraries].join(delimiter), '-d', output,...source,...production,harness,absent], {stdio:'inherit'})
  const run = (extra, main, args=[]) => process.stdout.write(execFileSync(bin('java'), ['-cp',[output,...extra,...libraries].join(delimiter),main,...args],{encoding:'utf8'}))
  run([actual], 'BackpackRegression')
  run([], 'BackpackAbsent')
  if (nativePresent) {
    const nativeCheck = write('BackpackNativeMixinChecks.java',
      readFileSync(join(root,'tools/templates/BackpackNativeMixinChecks.java.template'),'utf8'))
    execFileSync(bin('javac'), ['-proc:none','--release','25','-encoding','UTF-8','-cp',
      [output,actual,native,...libraries].join(delimiter),'-d',output,nativeCheck],{stdio:'inherit'})
    run([actual,native], 'BackpackNativeMixinChecks',['raw'])
    run([actual,native], 'BackpackNativeMixinChecks',['weave',native,
      join(output,pkg,'mixin/compat/SophisticatedBackpackTrinketsMixin.class'),
      'net.p3pp3rf1y.sophisticatedbackpacks.compat.trinkets.TrinketsCompat'])
    run([actual,native], 'BackpackNativeMixinChecks',['weave',native,
      join(output,pkg,'mixin/compat/SophisticatedBackpackTrinketTickMixin.class'),
      'net.p3pp3rf1y.sophisticatedbackpacks.compat.trinkets.BackpackTrinket'])
    run([actual,native], 'BackpackNativeMixinChecks',['patched'])
  } else {
    const marker=write(`${third}/compat/trinkets/TrinketsCompat.java`, 'package net.p3pp3rf1y.sophisticatedbackpacks.compat.trinkets; public class TrinketsCompat {}')
    execFileSync(bin('javac'), ['-d',native,marker],{stdio:'inherit'})
  }
  run([actual,native], 'BackpackRegression',['native'])
} finally {
  rmSync(directory,{recursive:true,force:true})
}
