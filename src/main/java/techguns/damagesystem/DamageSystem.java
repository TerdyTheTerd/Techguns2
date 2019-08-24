package techguns.damagesystem;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemShield;
import net.minecraft.item.ItemStack;
import net.minecraft.util.CombatRules;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import techguns.TGConfig;
import techguns.api.damagesystem.DamageType;
import techguns.api.npc.INpcTGDamageSystem;
import techguns.entities.npcs.NPCTurret;
import techguns.util.MathUtil;
import techguns.util.TGLogger;

public class DamageSystem {

	//This class should be used to return the damage that should be passed through the OnLivingAttack, not a complete replacement for that event
	public static float getDamageFactor(EntityLivingBase attacker, EntityLivingBase target) {
		if (attacker instanceof EntityPlayer && target instanceof EntityPlayer){
			if (FMLCommonHandler.instance().getMinecraftServerInstance().isPVPEnabled()){
				return TGConfig.damagePvP;
			} else {
				return 0.0f;
			}
		} else if (target instanceof EntityPlayer){
			if ( attacker instanceof NPCTurret){
				return TGConfig.damageTurretToPlayer;
			} else {
				return TGConfig.damageFactorNPC;
			}

		} else if (attacker instanceof EntityPlayer){
			return 1.0f;
		}
		
		return TGConfig.damageFactorNPC;
	}
	
	
	protected static Field ENT_rand = ReflectionHelper.findField(Entity.class, "rand", "field_70146_Z");
	
	protected static Field ELB_idleTime = ReflectionHelper.findField(EntityLivingBase.class, "idleTime", "field_70708_bq");
	
	protected static Field ELB_lastDamage = ReflectionHelper.findField(EntityLivingBase.class, "lastDamage", "field_110153_bc");
	
	protected static Field ELB_recentlyHit = ReflectionHelper.findField(EntityLivingBase.class, "recentlyHit", "field_70718_bc");
	protected static Field ELB_attackingPlayer = ReflectionHelper.findField(EntityLivingBase.class, "attackingPlayer", "field_70717_bb");
	
	protected static Field ELB_lastDamageSource = ReflectionHelper.findField(EntityLivingBase.class, "lastDamageSource", "field_189750_bF");
	protected static Field ELB_lastDamageStamp = ReflectionHelper.findField(EntityLivingBase.class, "lastDamageStamp", "field_189751_bG");
	
	protected static Method ELB_canBlockDamageSource = ReflectionHelper.findMethod(EntityLivingBase.class, "canBlockDamageSource", "func_184583_d", DamageSource.class);
	
	protected static Method ELB_damageShield = ReflectionHelper.findMethod(EntityLivingBase.class, "damageShield", "func_184590_k", float.class);
	
	protected static Method ELB_blockUsingShield = ReflectionHelper.findMethod(EntityLivingBase.class, "blockUsingShield", "func_190629_c", EntityLivingBase.class);
	
	protected static Method ELB_damageEntity = ReflectionHelper.findMethod(EntityLivingBase.class, "damageEntity", "func_70665_d", DamageSource.class, float.class);
	
	protected static Method ELB_setBeenAttacked = ReflectionHelper.findMethod(EntityLivingBase.class, "markVelocityChanged", "func_70018_K");
	
	protected static Method ELB_checkTotemDeathProtection = ReflectionHelper.findMethod(EntityLivingBase.class, "checkTotemDeathProtection", "func_190628_d", DamageSource.class);
	
	protected static Method ELB_getDeathSound = ReflectionHelper.findMethod(EntityLivingBase.class, "getDeathSound", "func_184615_bR");
	protected static Method ELB_getSoundVolume = ReflectionHelper.findMethod(EntityLivingBase.class, "getSoundVolume", "func_70599_aP");
	protected static Method ELB_getSoundPitch = ReflectionHelper.findMethod(EntityLivingBase.class, "getSoundPitch", "func_70647_i");
	
	protected static Method ELB_playHurtSound = ReflectionHelper.findMethod(EntityLivingBase.class, "playHurtSound", "func_184581_c", DamageSource.class);
	
	protected static Method ELB_applyPotionDamageCalculations = ReflectionHelper.findMethod(EntityLivingBase.class, "applyPotionDamageCalculations", "func_70672_c", DamageSource.class, float.class);
	protected static Method ELB_damageArmor = ReflectionHelper.findMethod(EntityLivingBase.class, "damageArmor", "func_70675_k", float.class);
	protected static Method ELB_attackPlayerFrom = ReflectionHelper.findMethod(EntityLivingBase.class, "attackEntityFrom", "func_70097_a", DamageSource.class, float.class);
	
	public static float getTotalArmorAgainstType(EntityPlayer ply, DamageType type){
		float value=0.0f;
		
		for(int i=0;i<4; i++){
			ItemStack armor = ply.inventory.armorInventory.get(i);//ply.inventory.armorInventory[i];
			if(armor!=null){
				Item item = armor.getItem();
				
				if (item instanceof ItemArmor){
					if(type==DamageType.PHYSICAL){
						value += ((ItemArmor) item).getArmorMaterial().getDamageReductionAmount(((ItemArmor)item).armorType);
					}
				}
				
			}
			
		}
		
		return value;
	}
	
	/**
	 * Default behavior when unspecified
	 */
	public static float getArmorAgainstDamageTypeDefault(EntityLivingBase elb, float armor, DamageType damageType){
		switch(damageType){
			case PHYSICAL:
			case PROJECTILE:
				return armor;
				
			case EXPLOSION:
			case ENERGY:
			case ICE:
			case LIGHTNING:
			case DARK:
				return armor*0.5f;
			case FIRE:
				if(elb.isImmuneToFire()){
					return armor*2;
				} else {
					return armor*0.5f;
				}
				
			case POISON:
				return 0;
			case RADIATION:
				return 0;
			case UNRESISTABLE:
			default:
				return 0;
		}
		
	}
	public static float getTotalCorrectedDamage(EntityLivingBase elb, DamageSource damageSrc, float damageAmount) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    	//Calculate corrected damage based on the damage type, this damage is pre-armor damage, and is only modified from any additional damage that should be applied view penetration or elemental types\
		TGLogger.logger_server.log(Level.INFO, "Corrected damage is: " +  damageAmount);
        return damageAmount;
	}
    
	//To-do correct the damage types so they act as bonus damage, i.e. do not cancel the damage for incendiary rounds if they have fire resistance, instead just remove the fire damage
    

    public static float calculateShieldDamage(EntityLivingBase ent, float amount, TGDamageSource source) {
    	ItemStack active = ent.getActiveItemStack();
		ShieldStats s = ShieldStats.getStats(active, ent);
    	if(s!=null) {
    		return s.getAmount(amount, source);
    	}
		return amount;
	}
    
    public static void livingHurt(EntityLivingBase elb, DamageSource damageSrc, float damageAmount) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    	damageAmount = ELB_applyArmorCalculations(elb,damageSrc, damageAmount);
        damageAmount = (Float)ELB_applyPotionDamageCalculations.invoke(elb, damageSrc, damageAmount);
        float f = damageAmount;
        damageAmount = Math.max(damageAmount - elb.getAbsorptionAmount(), 0.0F);
        elb.setAbsorptionAmount(elb.getAbsorptionAmount() - (f - damageAmount));
        damageAmount = net.minecraftforge.common.ForgeHooks.onLivingDamage(elb, damageSrc, damageAmount);

        if (damageAmount != 0.0F)
        {
        	float f1 = elb.getHealth();
        	elb.setHealth(f1 - damageAmount);
        	elb.getCombatTracker().trackDamage(damageSrc, f1, damageAmount);
        	elb.setAbsorptionAmount(elb.getAbsorptionAmount() - damageAmount);
        	
        }
    }
    
    /**
     * Reduces damage, depending on armor
     */
    public static float ELB_applyArmorCalculations(EntityLivingBase elb, DamageSource source, float damage) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException
    {
        if (!source.isUnblockable())
        {
            ELB_damageArmor.invoke(elb, damage);
            TGDamageSource dmgsrc = TGDamageSource.getFromGenericDamageSource(source);
            INpcTGDamageSystem tg = (INpcTGDamageSystem) elb;
            float toughness = (float)elb.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS).getAttributeValue();
            damage = (float) getDamageAfterAbsorb_TGFormula(damage, tg.getTotalArmorAgainstType(dmgsrc), toughness, dmgsrc.armorPenetration*4);
        }
        return damage;
    }
    
    /**
     * based on old 1.7 damage formula
     * @return
     */
    public static double getDamageAfterAbsorb_TGFormula(float damage, float totalArmor, float toughnessAttribute, float penetration)
    {
    	float pen = Math.max((penetration)-toughnessAttribute, 0);
    	double armor = MathUtil.clamp(totalArmor-pen, 0.0,24.0);
    	return damage * (1.0-armor/25.0);
    }
}
