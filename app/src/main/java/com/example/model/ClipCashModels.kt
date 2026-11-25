package com.example.model

import androidx.annotation.DrawableRes
import com.example.R

enum class ClipCategory(val label: String, val searchTerm: String) {
    ALL("Featured", "trending shorts"),
    TRENDING("Trending", "viral shorts"),
    MUSIC("Music", "music shorts"),
    GAMING("Gaming", "gaming shorts"),
    COMEDY("Comedy", "comedy funny shorts"),
    TECH("Tech", "tech gadgets shorts")
}

data class VideoClip(
    val id: String,
    val videoId: String,
    val title: String,
    val sourceLabel: String = "ClipCash",
    val category: ClipCategory,
    val durationSeconds: Int = 15,
    val watchTargetSeconds: Int = 10,
    val rewardCoins: Int = 10,
    val thumbnailUrl: String? = null,
    @DrawableRes val fallbackThumbResId: Int = R.drawable.thumb_clip_neon_pulse,
    val description: String = "Watch on ClipCash to earn instant coins.",
    val remoteUuid: String? = null,
    val platform: String = "youtube",
    val rewardEligible: Boolean = true,
    val videoUrl: String? = null
)

enum class QuestMetric {
    CLIPS_WATCHED,
    GAMES_PLAYED,
    TOTAL_COINS_EARNED,
    DAILY_CHECKIN
}

data class QuestItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val rewardCoins: Int,
    val targetValue: Int,
    val metric: QuestMetric
)

data class RewardOption(
    val id: String,
    val title: String,
    val brand: String,
    val cashLabel: String,
    val coinCost: Int,
    val inputHint: String,
    val badge: String
)

enum class TransactionType(val label: String) {
    VIDEO("Watched Clip"),
    GAME("Mini-Game"),
    BONUS("Daily Bonus"),
    QUEST("Quest Claimed"),
    REDEEM("Cash Out")
}

data class NationalityOption(
    val countryName: String,
    val nationality: String,
    val isoCode: String,
    val dialCode: String,
    val flagEmoji: String
) {
    val displayLabel: String
        get() = "$flagEmoji $nationality ($countryName)"
}

object ClipCatalog {
    const val VIDEO_REWARD_COINS = 10
    const val USD_PER_100_COINS = 0.06
    const val MIN_WITHDRAW_USD = 5.0
    const val MIN_WITHDRAW_COINS = 8334

    fun coinsToUsd(coins: Int): Double = (coins / 100.0) * USD_PER_100_COINS

    val nationalities: List<NationalityOption> = listOf(
        NationalityOption("United States", "American", "US", "+1", "🇺🇸"),
        NationalityOption("United Kingdom", "British", "GB", "+44", "🇬🇧"),
        NationalityOption("Nigeria", "Nigerian", "NG", "+234", "🇳🇬"),
        NationalityOption("Canada", "Canadian", "CA", "+1", "🇨🇦"),
        NationalityOption("India", "Indian", "IN", "+91", "🇮🇳"),
        NationalityOption("Ghana", "Ghanaian", "GH", "+233", "🇬🇭"),
        NationalityOption("Kenya", "Kenyan", "KE", "+254", "🇰🇪"),
        NationalityOption("South Africa", "South African", "ZA", "+27", "🇿🇦"),
        NationalityOption("Australia", "Australian", "AU", "+61", "🇦🇺"),
        NationalityOption("Germany", "German", "DE", "+49", "🇩🇪"),
        NationalityOption("France", "French", "FR", "+33", "🇫🇷"),
        NationalityOption("Spain", "Spanish", "ES", "+34", "🇪🇸"),
        NationalityOption("Italy", "Italian", "IT", "+39", "🇮🇹"),
        NationalityOption("Netherlands", "Dutch", "NL", "+31", "🇳🇱"),
        NationalityOption("Brazil", "Brazilian", "BR", "+55", "🇧🇷"),
        NationalityOption("Mexico", "Mexican", "MX", "+52", "🇲🇽"),
        NationalityOption("Philippines", "Filipino", "PH", "+63", "🇵🇭"),
        NationalityOption("Indonesia", "Indonesian", "ID", "+62", "🇮🇩"),
        NationalityOption("Pakistan", "Pakistani", "PK", "+92", "🇵🇰"),
        NationalityOption("Bangladesh", "Bangladeshi", "BD", "+880", "🇧🇩"),
        NationalityOption("Egypt", "Egyptian", "EG", "+20", "🇪🇬"),
        NationalityOption("United Arab Emirates", "Emirati", "AE", "+971", "🇦🇪"),
        NationalityOption("Saudi Arabia", "Saudi", "SA", "+966", "🇸🇦"),
        NationalityOption("Japan", "Japanese", "JP", "+81", "🇯🇵"),
        NationalityOption("South Korea", "South Korean", "KR", "+82", "🇰🇷"),
        NationalityOption("Singapore", "Singaporean", "SG", "+65", "🇸🇬"),
        NationalityOption("Malaysia", "Malaysian", "MY", "+60", "🇲🇾"),
        NationalityOption("Ireland", "Irish", "IE", "+353", "🇮🇪"),
        NationalityOption("New Zealand", "New Zealander", "NZ", "+64", "🇳🇿"),
        NationalityOption("Jamaica", "Jamaican", "JM", "+1-876", "🇯🇲")
    )

    val fallbackClips: List<VideoClip> = listOf(
        VideoClip(
            id = "fallback_1",
            videoId = "M7lc1UVf-VE",
            title = "ClipCash Featured Creator Mix",
            sourceLabel = "ClipCash Originals",
            category = ClipCategory.ALL,
            watchTargetSeconds = 10,
            rewardCoins = VIDEO_REWARD_COINS,
            fallbackThumbResId = R.drawable.thumb_clip_neon_pulse,
            description = "Curated ClipCash video stream ready for instant rewards."
        ),
        VideoClip(
            id = "fallback_2",
            videoId = "aqz-KE-bpKQ",
            title = "Big Buck Cinema Showcase",
            sourceLabel = "ClipCash Cinema",
            category = ClipCategory.TRENDING,
            watchTargetSeconds = 10,
            rewardCoins = VIDEO_REWARD_COINS,
            fallbackThumbResId = R.drawable.thumb_clip_aurora_flow,
            description = "Animated showcase featured on ClipCash."
        ),
        VideoClip(
            id = "fallback_3",
            videoId = "jfKfPfyJRdk",
            title = "Chill Beats Radio Stream",
            sourceLabel = "ClipCash Music",
            category = ClipCategory.MUSIC,
            watchTargetSeconds = 10,
            rewardCoins = VIDEO_REWARD_COINS,
            fallbackThumbResId = R.drawable.thumb_clip_prism_waves,
            description = "Relaxing study and focus soundtrack on ClipCash."
        )
    )

    val quests: List<QuestItem> = listOf(
        QuestItem(
            id = "quest_first_checkin",
            title = "Daily Streak Starter",
            subtitle = "Claim your Daily Check-In bonus",
            rewardCoins = 40,
            targetValue = 1,
            metric = QuestMetric.DAILY_CHECKIN
        ),
        QuestItem(
            id = "quest_watch_1",
            title = "First Clip Fan",
            subtitle = "Watch 1 ClipCash video to completion",
            rewardCoins = 75,
            targetValue = 1,
            metric = QuestMetric.CLIPS_WATCHED
        ),
        QuestItem(
            id = "quest_play_1",
            title = "Arcade Challenger",
            subtitle = "Finish 1 round of Coin Catcher or Lucky Spin",
            rewardCoins = 60,
            targetValue = 1,
            metric = QuestMetric.GAMES_PLAYED
        ),
        QuestItem(
            id = "quest_watch_3",
            title = "Clip Binge Streak",
            subtitle = "Watch 3 ClipCash videos and collect rewards",
            rewardCoins = 150,
            targetValue = 3,
            metric = QuestMetric.CLIPS_WATCHED
        ),
        QuestItem(
            id = "quest_earn_500",
            title = "Gold Collector",
            subtitle = "Earn 500 total lifetime coins",
            rewardCoins = 120,
            targetValue = 500,
            metric = QuestMetric.TOTAL_COINS_EARNED
        )
    )

    val rewards: List<RewardOption> = listOf(
        RewardOption(
            id = "reward_paypal_5",
            title = "$5.00 Instant Payout",
            brand = "PayPal Cash",
            cashLabel = "$5.00",
            coinCost = MIN_WITHDRAW_COINS,
            inputHint = "PayPal email address",
            badge = "Min Cash Out"
        ),
        RewardOption(
            id = "reward_amazon_5",
            title = "$5.00 Gift Card",
            brand = "Amazon.com",
            cashLabel = "$5.00",
            coinCost = MIN_WITHDRAW_COINS,
            inputHint = "Delivery email address",
            badge = "Popular"
        ),
        RewardOption(
            id = "reward_gplay_5",
            title = "$5.00 Play Credit",
            brand = "Google Play",
            cashLabel = "$5.00",
            coinCost = MIN_WITHDRAW_COINS,
            inputHint = "Google account email",
            badge = "Instant"
        ),
        RewardOption(
            id = "reward_paypal_10",
            title = "$10.00 Cash Transfer",
            brand = "PayPal Cash",
            cashLabel = "$10.00",
            coinCost = 16667,
            inputHint = "PayPal email address",
            badge = "Best Value"
        )
    )
}
