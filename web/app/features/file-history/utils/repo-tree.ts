/**
 * Repository file tree for the Files page, built on the checkpoint file-tree helpers.
 */
import {
  buildFileTree,
  collapseSingleChildChains,
  type FileTreeNode,
} from '~/features/checkpoint/utils/file-tree'
import type { FileEntry } from '../types/file-history.types'

export function buildRepoTree(files: FileEntry[]): FileTreeNode[] {
  return collapseSingleChildChains(
    buildFileTree(files.map((f) => ({ file: f.path, additions: 0, deletions: 0 })))
  )
}

/** Folder paths (as used by the tree: collapsed nodes use their deepest path) that are ancestors of `path` */
export function ancestorFolders(nodes: FileTreeNode[], path: string): string[] {
  const out: string[] = []
  function walk(list: FileTreeNode[]): boolean {
    for (const n of list) {
      if (n.isFile) {
        if (n.fullPath === path) return true
      } else if (path.startsWith(n.fullPath + '/')) {
        out.push(n.fullPath)
        if (walk(n.children)) return true
      }
    }
    return false
  }
  walk(nodes)
  return out
}

/** Folder paths containing at least one of the given file paths */
export function foldersContaining(nodes: FileTreeNode[], paths: Iterable<string>): Set<string> {
  const set = new Set<string>()
  for (const p of paths) ancestorFolders(nodes, p).forEach((f) => set.add(f))
  return set
}
