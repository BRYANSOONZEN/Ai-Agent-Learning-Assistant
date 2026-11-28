<script setup>
import { stages } from '../../data/demo'
defineProps({ currentStage: String })
</script>

<template>
  <div class="stage-rail">
    <div v-for="(stage, index) in stages" :key="stage.id" class="stage-node" :class="{ active: currentStage === stage.id, passed: index < stages.findIndex(item => item.id === currentStage) }">
      <div class="stage-circle"><span v-if="index < stages.findIndex(item => item.id === currentStage)">✓</span><span v-else>{{ stage.short }}</span></div>
      <div><strong>{{ stage.label }}</strong><small>{{ stage.caption }}</small></div>
    </div>
  </div>
</template>
