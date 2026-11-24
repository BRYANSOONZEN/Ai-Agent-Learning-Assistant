import { computed, reactive } from 'vue'

/*
 * Whether the student is in the middle of something the study assistant must not interrupt:
 * an exercise before it is submitted, or an AI dialogue that is still going.
 * Those components call hold(key) while busy and release(key) when done or unmounted;
 * the assistant hides itself while anything is held (docs/计划-个人学习助手.md 第 10.2 节).
 */
const holds = reactive(new Set())

export function hold(key) {
  holds.add(key)
}

export function release(key) {
  holds.delete(key)
}

export const studentBusy = computed(() => holds.size > 0)
