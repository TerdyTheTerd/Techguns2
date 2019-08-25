package techguns.damagesystem;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.fml.common.FMLCommonHandler;
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
	 * This is only used for mobs
	 */
	public static float getArmorAgainstDamageTypeMobs(EntityLivingBase elb, float armor, DamageType damageType){
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
		//Need to check players armor, as different types should deal different damage
		switch(damageSrc.damageType.toString()){ //Check bullet type?
			case "PHYSICAL":
			case "PROJECTILE":
			case "EXPLOSION":
			case "ENERGY":
			case "ICE":
			case "LIGHTNING":
			case "DARK":
			case "FIRE":
			case "POISON":
			case "RADIATION":
			case "UNRESISTABLE":
			default:
				return damageAmount;
		}
	}
    
	//To-do correct the damage types so they act as bonus damage, i.e. do not cancel the damage for incendiary rounds if they have fire resistance, instead just remove the fire damage
    
	public static float getKnockBack(TGDamageSource src) {
		if(src.knockbackOnShieldBlock) {
			return src.knockbackMultiplier * src.armorPenetration;
		} else {
			return 0.0f;
		}
	}
    public static float calculateShieldDamage(EntityLivingBase ent, float amount, TGDamageSource source) {
    	ItemStack active = ent.getActiveItemStack();
		ShieldStats s = ShieldStats.getStats(active, ent);
    	if(s!=null) {
    		return s.getAmount(amount, source);
    	}
		return amount;
	}
    
    public static float getModifiedDamage(EntityLivingBase ent, DamageSource source, float amount) throws IllegalArgumentException, IllegalAccessException, InvocationTargetException
    {
        //if (!net.minecraftforge.common.ForgeHooks.onLivingAttack(this, source, amount)) return false;
    	
    	TGDamageSource dmgsrc = TGDamageSource.getFromGenericDamageSource(source);
            //ent.idleTime = 0;
    	TGLogger.logger_server.log(Level.INFO, "Damage type is " + dmgsrc.getDamageType().toString() + " " + dmgsrc.isPiercingRound);
    	if(dmgsrc.damageType == DamageType.RADIATION) {
    		dmgsrc.setDamageBypassesArmor();
    		TGLogger.logger_server.log(Level.INFO, "Damage set to bypass armor");
    	}
            ELB_idleTime.setInt(ent, 0);

                if(ent instanceof EntityPlayer) {
                	amount = EP_applyArmorCalculations((EntityPlayer)ent, dmgsrc, amount);
                }
                if (!dmgsrc.ignoreHurtresistTime && ((float)ent.hurtResistantTime > (float)ent.maxHurtResistantTime / 2.0F))
                {
                	TGLogger.logger_server.log(Level.INFO, "Corrected damage is: " +  amount);
                    return amount;
                }
                else
                {
                	TGLogger.logger_server.log(Level.INFO, "Corrected damage is: " +  amount);
                    return amount;

                }
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
        	if(!(elb instanceof EntityPlayer)) {
        		ELB_damageArmor.invoke(elb, damage);
        		TGDamageSource dmgsrc = TGDamageSource.getFromGenericDamageSource(source);
        		INpcTGDamageSystem tg = (INpcTGDamageSystem) elb;
        		float toughness = (float)elb.getEntityAttribute(SharedMonsterAttributes.ARMOR_TOUGHNESS).getAttributeValue();
        		damage = (float) getDamageAfterAbsorb_TGFormula(damage, tg.getTotalArmorAgainstType(dmgsrc), toughness, dmgsrc.armorPenetration*4, dmgsrc.isPiercingRound);//Why are multiplying by 4?
        	} else {
        		//Get toughness of the armor the player is wearing
        	}
        }
        return damage;
    }
    
    //TODO Update to newer damage formula
    public static double getDamageAfterAbsorb_TGFormula(float damage, float totalArmor, float toughnessAttribute, float penetration, boolean isPiercing)
    {
    	//What about when armor is 0?
    	float toughnessRatio = 0f;
    	if(totalArmor > 0) {
    		toughnessRatio = (toughnessAttribute / totalArmor);
    	}
    	float pen = Math.max((penetration - (toughnessRatio * penetration)), 0f);
    	TGLogger.logger_server.log(Level.INFO, "Final pen is " + pen);
    	float bounusPiercingDamage = 0f;
    	//This could very well be busted, as on highest tier armor this will add an additional 11 damage. Then again it should balance with high tier energy based armors, to help break the energy shields faster
    	if(isPiercing) {
    		bounusPiercingDamage += (float) (Math.round(((totalArmor * .25) + (toughnessAttribute * .5)) * 2) / 2.0);
    		TGLogger.logger_server.log(Level.INFO, "Bonus piercing damage is " + bounusPiercingDamage);
    	}
    	TGLogger.logger_server.log(Level.INFO, "Penetration of " + penetration + " will ignore " + (totalArmor * pen) + " armor out of " + totalArmor + " total armor with toughness " + toughnessAttribute);
    	double armor = totalArmor - MathUtil.clamp(totalArmor * pen, 0.0,totalArmor);
    	return damage * (1.0-armor/25.0) + bounusPiercingDamage;
    }
    
    //Calculate modified damage based on current armor, damage type and projecticle penetration, if any
    public static float EP_applyArmorCalculations(EntityPlayer player, TGDamageSource source, float damage) {
    	//Check each armor slot for armor, calculate total armor, then apply pen ratio for total damage
    	float totalArmorRating = 0f;
    	float totalToughness = 0f;
    	for(ItemStack item : player.getArmorInventoryList()) {
    		if(!item.isEmpty()) {
    			Item armorPiece = item.getItem();
    			totalArmorRating += ((ItemArmor)armorPiece).getArmorMaterial().getDamageReductionAmount(net.minecraft.entity.EntityLiving.getSlotForItemStack(item));
    			totalToughness += ((ItemArmor)armorPiece).getArmorMaterial().getToughness();
    		}
    	}
    	//Now we have the total player armor, calculate the penetration amount
    	float totalDamage = (float) getDamageAfterAbsorb_TGFormula(damage, totalArmorRating, totalToughness, source.armorPenetration, source.isPiercingRound);
    	//System.out.printf("Calucalating damage: Base Damage-%f, Penetration-%f, Total Armor-%f, Armor Toughness-%f, Calculated Damage-%f", damage, source.armorPenetration, totalArmorRating, totalToughness, totalDamage);
    	return (float) Math.max((Math.round(totalDamage * 2) / 2.0), 0f);
    }
}
