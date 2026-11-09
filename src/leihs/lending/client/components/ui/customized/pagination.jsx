/**
 * Customized for Leihs
 * - asChild support on PaginationLink
 * - asChild support and `text` prop on PaginationPrevious and PaginationNext
 * - PaginationPrevious and PaginationNext always show their text
 * */

import * as React from "react"
import { cn } from "cn"
import {
  ChevronLeftIcon,
  ChevronRightIcon,
  MoreHorizontalIcon,
} from "lucide-react"
import { Slot } from "radix-ui"

import { buttonVariants } from "@/components/ui/button"

function Pagination({ className, ...props }) {
  return (
    <nav
      role="navigation"
      aria-label="pagination"
      data-slot="pagination"
      className={cn("mx-auto flex w-full justify-center", className)}
      {...props}
    />
  )
}

function PaginationContent({ className, ...props }) {
  return (
    <ul
      data-slot="pagination-content"
      className={cn("flex flex-row items-center gap-1", className)}
      {...props}
    />
  )
}

function PaginationItem({ ...props }) {
  return <li data-slot="pagination-item" {...props} />
}

function PaginationLink({
  className,
  isActive,
  size = "icon",
  asChild = false,
  ...props
}) {
  const Comp = asChild ? Slot.Root : "a"
  return (
    <Comp
      aria-current={isActive ? "page" : undefined}
      data-slot="pagination-link"
      data-active={isActive}
      className={cn(
        buttonVariants({
          variant: isActive ? "outline" : "ghost",
          size,
        }),
        className,
      )}
      {...props}
    />
  )
}

function withChild(asChild, children, content) {
  return asChild
    ? React.cloneElement(React.Children.only(children), {}, content)
    : content
}

function PaginationPrevious({
  className,
  asChild = false,
  children,
  text = "Previous",
  ...props
}) {
  return (
    <PaginationLink
      aria-label="Go to previous page"
      size="default"
      className={cn("gap-1 pl-2.5", className)}
      asChild={asChild}
      {...props}
    >
      {withChild(
        asChild,
        children,
        <>
          <ChevronLeftIcon />
          <span>{text}</span>
        </>,
      )}
    </PaginationLink>
  )
}

function PaginationNext({
  className,
  asChild = false,
  children,
  text = "Next",
  ...props
}) {
  return (
    <PaginationLink
      aria-label="Go to next page"
      size="default"
      className={cn("gap-1 pr-2.5", className)}
      asChild={asChild}
      {...props}
    >
      {withChild(
        asChild,
        children,
        <>
          <span>{text}</span>
          <ChevronRightIcon />
        </>,
      )}
    </PaginationLink>
  )
}

function PaginationEllipsis({ className, ...props }) {
  return (
    <span
      aria-hidden
      data-slot="pagination-ellipsis"
      className={cn("flex size-9 items-center justify-center", className)}
      {...props}
    >
      <MoreHorizontalIcon className="size-4" />
      <span className="sr-only">More pages</span>
    </span>
  )
}

export {
  Pagination,
  PaginationContent,
  PaginationLink,
  PaginationItem,
  PaginationPrevious,
  PaginationNext,
  PaginationEllipsis,
}
