<script setup lang="ts">defineProps<{ title: string; owner: string }>()</script>
<template><section class="page-heading"><div class="eyebrow">工作模块</div><h1>{{ title }}</h1><p>{{ owner }} 将在此接入页面。当前可返回工作台使用账号和角色功能。</p><RouterLink to="/" class="inline-link">返回工作台 →</RouterLink></section></template>
