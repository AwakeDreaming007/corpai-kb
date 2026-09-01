<script setup>
// 应用入口壳，仅负责路由出口与统一过渡
</script>

<template>
  <router-view v-slot="{ Component }">
    <transition name="fade-slide" mode="out-in">
      <component :is="Component" />
    </transition>
  </router-view>
</template>
