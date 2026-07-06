import React from 'react';
import { Tabs } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';

type IoniconsName = React.ComponentProps<typeof Ionicons>['name'];

interface TabConfig {
  name: string;
  title: string;
  icon: IoniconsName;
  activeIcon: IoniconsName;
}

const TABS: TabConfig[] = [
  { name: 'index', title: '홈', icon: 'home-outline', activeIcon: 'home' },
  { name: 'insight', title: '인사이트', icon: 'bulb-outline', activeIcon: 'bulb' },
  { name: 'trading/index', title: '모의투자', icon: 'trending-up-outline', activeIcon: 'trending-up' },
  { name: 'community/index', title: '커뮤니티', icon: 'people-outline', activeIcon: 'people' },
  { name: 'ranking', title: '랭킹', icon: 'trophy-outline', activeIcon: 'trophy' },
];

export default function TabsLayout(): React.JSX.Element {
  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarStyle: { backgroundColor: '#0A0E1A', borderTopColor: '#2C2C2E' },
        tabBarActiveTintColor: '#FF3B30',
        tabBarInactiveTintColor: '#636366',
      }}
    >
      {TABS.map((tab) => (
        <Tabs.Screen
          key={tab.name}
          name={tab.name}
          options={{
            title: tab.title,
            tabBarIcon: ({ focused, color, size }) => (
              <Ionicons
                name={focused ? tab.activeIcon : tab.icon}
                size={size}
                color={color}
              />
            ),
          }}
        />
      ))}
    </Tabs>
  );
}
