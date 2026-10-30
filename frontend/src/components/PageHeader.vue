<script setup>
// Title row at the top of EVERY page (Studywise "page intro"): small English label, heading, one line of
// description. The label comes from the route (meta.eyebrow) unless passed in. Page-level buttons go in the slot.
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const props = defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  eyebrow: { type: String, default: '' },
  back: { type: Boolean, default: false },
})

const route = useRoute()
const label = computed(() => props.eyebrow || route.meta.eyebrow || '')
</script>

<template>
  <div class="page-header">
    <div class="left">
      <el-button v-if="back" icon="ArrowLeft" circle size="small" @click="$router.back()" />
      <div>
        <span v-if="label" class="sw-eyebrow">{{ label }}</span>
        <h2 class="title">{{ title }}</h2>
        <p v-if="description" class="description">{{ description }}</p>
      </div>
    </div>
    <div class="actions"><slot /></div>
  </div>
</template>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: var(--app-space-md);
  margin: 7px 0 22px;
}

.left {
  display: flex;
  align-items: center;
  gap: var(--app-space-md);
}

.title {
  margin: 9px 0 5px;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: -0.7px;
}

.description {
  margin: 0;
  max-width: 60em;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.6;
}

.actions {
  display: flex;
  gap: var(--app-space-sm);
}
</style>
