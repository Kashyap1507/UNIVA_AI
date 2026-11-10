"""
Seed subscription plans in Firestore
Creates 4 tiers: FREE, BASIC, PREMIUM, PRO
"""

import sys
from pathlib import Path

# Add parent directory to path to import app modules
sys.path.insert(0, str(Path(__file__).parent.parent.parent))

from app.models.subscription_plan import SubscriptionPlan
from app.services.firebase_service import get_firestore_client, initialize_firebase


def seed_subscription_plans():
    """Seed subscription plans in Firestore"""
    print("🌱 Seeding subscription plans...")

    # Initialize Firebase first
    initialize_firebase()
    db = get_firestore_client()

    # Define subscription plans
    plans = [
        {
            "tier": "free",
            "name": "Free",
            "description": "Perfect for trying out Wizly AI",
            "price_monthly": 0.0,
            "price_yearly": 0.0,
            "limits": {
                "work_checks": 3,
                "practice_sets": 2,
                "flashcard_sets": 10,
            },
            "features": [
                "3 work checks per day",
                "2 practice sets per day",
                "10 AI flashcard sets per day",
                "Tutor chat with citations from your courses",
                "Standard support",
            ],
            "is_active": True,
            "is_popular": False,
            "display_order": 0,
        },
        {
            "tier": "basic",
            "name": "Basic",
            "description": "For a regular course load",
            "price_monthly": 4.99,
            "price_yearly": 49.99,  # ~17% discount
            "limits": {
                "work_checks": 10,
                "practice_sets": 5,
                "flashcard_sets": 30,
            },
            "features": [
                "10 work checks per day",
                "5 practice sets per day",
                "30 AI flashcard sets per day",
                "Tutor chat with citations from your courses",
                "Priority support",
                "Course progress and grade tracking",
            ],
            "is_active": True,
            "is_popular": False,
            "display_order": 1,
        },
        {
            "tier": "premium",
            "name": "Premium",
            "description": "Best for dedicated students",
            "price_monthly": 9.99,
            "price_yearly": 99.99,  # ~17% discount
            "limits": {
                "work_checks": 50,
                "practice_sets": 20,
                "flashcard_sets": -1,  # -1 = unlimited
            },
            "features": [
                "50 work checks per day",
                "20 practice sets per day",
                "Unlimited AI flashcard sets",
                "Detailed feedback on your work",
                "Detailed analytics",
                "Exam-countdown study plans",
                "Priority email support",
                "Ad-free experience",
            ],
            "is_active": True,
            "is_popular": True,  # Mark as "Most Popular"
            "display_order": 2,
        },
        {
            "tier": "pro",
            "name": "Pro",
            "description": "Unlimited everything for power users",
            "price_monthly": 14.99,
            "price_yearly": 149.99,  # ~17% discount
            "limits": {
                "work_checks": -1,  # -1 = unlimited
                "practice_sets": -1,  # -1 = unlimited
                "flashcard_sets": -1,  # -1 = unlimited
            },
            "features": [
                "Unlimited work checks",
                "Unlimited practice sets",
                "Unlimited AI flashcard sets",
                "Fastest AI processing",
                "Advanced analytics dashboard",
                "Mock exams for every course",
                "24/7 priority support",
                "Ad-free experience",
                "Early access to new features",
                "Multiple device sync",
            ],
            "is_active": True,
            "is_popular": False,
            "display_order": 3,
        },
    ]

    # Create or update each plan
    created_count = 0
    updated_count = 0

    for plan_data in plans:
        tier = plan_data["tier"]

        # Check if plan already exists
        existing_plan = SubscriptionPlan.get_by_tier(db, tier)

        if existing_plan:
            print(f"⚠️  Plan '{tier}' already exists, updating...")
            # Update existing plan
            for key, value in plan_data.items():
                setattr(existing_plan, key, value)
            existing_plan.save(db)
            updated_count += 1
        else:
            # Create new plan
            new_plan = SubscriptionPlan(**plan_data)
            is_valid, error_msg = new_plan.validate()

            if not is_valid:
                print(f"❌ Validation error for '{tier}': {error_msg}")
                continue

            new_plan.save(db)
            print(f"✅ Created plan: {tier} - {plan_data['name']}")
            created_count += 1

    print(f"\n🎉 Seeding complete!")
    print(f"   Created: {created_count} plans")
    print(f"   Updated: {updated_count} plans")
    print(f"   Total: {len(plans)} plans")


if __name__ == "__main__":
    try:
        seed_subscription_plans()
    except Exception as e:
        print(f"❌ Error seeding subscription plans: {str(e)}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
