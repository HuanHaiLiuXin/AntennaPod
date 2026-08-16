package de.danoeh.antennapod.ui.preferences.screen.about

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.transition.MaterialSharedAxis
import de.danoeh.antennapod.ui.preferences.R

/**
 * Displays the 'about->Contributors' pager screen.
 */
class ContributorsPagerFragment : Fragment() {
    companion object {
        private const val POS_DEVELOPERS = 0
        private const val POS_TRANSLATORS = 1
        private const val POS_SPECIAL_THANKS = 2
        private const val TOTAL_COUNT = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setEnterTransition(MaterialSharedAxis(MaterialSharedAxis.X, true))
        setReturnTransition(MaterialSharedAxis(MaterialSharedAxis.X, false))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)

        val rootView = inflater.inflate(R.layout.pager_fragment, container, false)
        val viewPager = rootView.findViewById<ViewPager2>(R.id.viewpager)
        viewPager.setAdapter(StatisticsPagerAdapter(this))
        // Give the TabLayout the ViewPager
        val tabLayout = rootView.findViewById<TabLayout>(R.id.sliding_tabs)
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            when (position) {
                POS_DEVELOPERS -> tab.setText(R.string.developers)
                POS_TRANSLATORS -> tab.setText(R.string.translators)
                POS_SPECIAL_THANKS -> tab.setText(R.string.special_thanks)
                else -> Unit
            }
        }.attach()

        rootView.findViewById<View>(R.id.toolbar).setVisibility(View.GONE)

        return rootView
    }

    override fun onStart() {
        super.onStart()
        (getActivity() as AppCompatActivity).getSupportActionBar()!!.setTitle(R.string.contributors)
    }

    class StatisticsPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                POS_TRANSLATORS -> TranslatorsFragment()
                POS_SPECIAL_THANKS -> SpecialThanksFragment()
                else -> DevelopersFragment()
            }
        }

        override fun getItemCount(): Int {
            return TOTAL_COUNT
        }
    }
}
